using System.Diagnostics;
using System.IO;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Text;
using Microsoft.Win32.SafeHandles;

namespace AgentMeter.Windows.Providers.Gemini;

// Read only the official app's cookie file through an existing, read-only duplicate.
// No process memory, privilege elevation, file writes, or changes to Gemini are needed.
internal static class GeminiCookieFile
{
    internal static byte[] ReadLockedDatabase(string path)
    {
        var expected = Path.GetFullPath(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
            "Gemini", "Network", "Cookies"));
        if (!string.Equals(Path.GetFullPath(path), expected, StringComparison.OrdinalIgnoreCase))
            throw new IOException("Unsupported session store.");
        var appRoot = Path.GetFullPath(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "Google", "Gemini")) + Path.DirectorySeparatorChar;
        var deadline = Stopwatch.StartNew();
        foreach (var process in Process.GetProcessesByName("Gemini"))
        {
            using (process)
            {
                if (deadline.Elapsed > TimeSpan.FromSeconds(3)) break;
                using var source = OpenProcess(0x0440, false, process.Id); // Query + duplicate handles only.
                if (source.IsInvalid) continue;
                var image = new StringBuilder(32768);
                var imageLength = image.Capacity;
                if (!QueryFullProcessImageName(source, 0, image, ref imageLength) ||
                    !image.ToString().StartsWith(appRoot, StringComparison.OrdinalIgnoreCase)) continue;
                var snapshot = ReadFromProcess(source, expected, deadline);
                if (snapshot != null) return snapshot;
            }
        }
        throw new IOException("Local session store temporarily unavailable.");
    }

    private static byte[]? ReadFromProcess(SafeProcessHandle source, string expected, Stopwatch deadline)
    {
        // ProcessHandleInformation is a Windows 8+ native handle snapshot. Bound all native buffers.
        var size = 65536;
        for (var attempt = 0; attempt < 3 && size <= 4 * 1024 * 1024; attempt++)
        {
            var buffer = Marshal.AllocHGlobal(size);
            try
            {
                var status = NtQueryInformationProcess(source, 51, buffer, size, out var needed);
                if (status == unchecked((int)0xC0000004)) { size = Math.Max(size * 2, needed); continue; }
                if (status < 0) return null;
                var count = Marshal.ReadIntPtr(buffer).ToInt64();
                var entrySize = Marshal.SizeOf<HandleEntry>();
                var offset = 2 * IntPtr.Size;
                if (count < 0 || count > (size - offset) / entrySize) return null;
                StringBuilder? name = null;
                for (var i = 0; i < count; i++)
                {
                    if (deadline.Elapsed > TimeSpan.FromSeconds(3)) return null;
                    var entry = Marshal.PtrToStructure<HandleEntry>(buffer + offset + i * entrySize);
                    if ((entry.Access & 1) == 0 || !DuplicateHandle(source, entry.Handle, GetCurrentProcess(),
                            out var file, 1, false, 0)) continue; // FILE_READ_DATA, never duplicate write access.
                    using (file)
                    {
                        if (GetFileType(file) != 1) continue; // Disk files only; never query pipe object names.
                        name ??= new StringBuilder(32768);
                        name.Clear();
                        var length = GetFinalPathNameByHandle(file, name, (uint)name.Capacity, 0);
                        if (length == 0 || length >= name.Capacity) continue;
                        var actual = name.ToString();
                        if (actual.StartsWith(@"\\?\", StringComparison.Ordinal)) actual = actual[4..];
                        if (!string.Equals(actual, expected, StringComparison.OrdinalIgnoreCase)) continue;
                        return ReadSnapshot(file, expected);
                    }
                }
                return null;
            }
            finally { Marshal.FreeHGlobal(buffer); }
        }
        return null;
    }

    private static byte[] ReadSnapshot(SafeFileHandle file, string path)
    {
        // Do not read uncommitted rollback pages or a database with outstanding WAL frames.
        EnsureCommitted(path);
        if (!GetFileSizeEx(file, out var length) || length is < 100 or > 16 * 1024 * 1024)
            throw new IOException("Unsupported session store size.");
        using var mapping = CreateFileMapping(file, IntPtr.Zero, 2, 0, 0, null); // PAGE_READONLY.
        if (mapping.IsInvalid) throw new IOException("Session store mapping unavailable.");
        var view = MapViewOfFile(mapping, 4, 0, 0, (nuint)length); // FILE_MAP_READ.
        if (view == IntPtr.Zero) throw new IOException("Session store view unavailable.");
        var first = new byte[(int)length];
        var second = new byte[(int)length];
        try
        {
            // Mapping leaves Gemini's shared file position unchanged, unlike seeking a duplicated handle.
            Marshal.Copy(view, first, 0, first.Length);
            Marshal.Copy(view, second, 0, second.Length);
            EnsureCommitted(path);
            if (!GetFileSizeEx(file, out var after) || after != length || !first.AsSpan().SequenceEqual(second) ||
                !first.AsSpan(0, 16).SequenceEqual("SQLite format 3\0"u8) || first[18] != 1 || first[19] != 1)
                throw new IOException("Session store changed during read.");
            return first;
        }
        catch { CryptographicOperations.ZeroMemory(first); throw; }
        finally { CryptographicOperations.ZeroMemory(second); UnmapViewOfFile(view); }
    }

    private static void EnsureCommitted(string path)
    {
        foreach (var suffix in new[] { "-journal", "-wal" })
            if (File.Exists(path + suffix) && new FileInfo(path + suffix).Length > 0)
                throw new IOException("Session store transaction in progress.");
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct HandleEntry
    {
        public IntPtr Handle;
        public nuint HandleCount, PointerCount;
        public uint Access, ObjectType, Attributes, Reserved;
    }

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern SafeProcessHandle OpenProcess(uint access, bool inherit, int processId);
    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern bool QueryFullProcessImageName(SafeProcessHandle process, uint flags, StringBuilder name, ref int size);
    [DllImport("ntdll.dll")]
    private static extern int NtQueryInformationProcess(SafeProcessHandle process, int infoClass, IntPtr buffer, int length, out int needed);
    [DllImport("kernel32.dll")]
    private static extern IntPtr GetCurrentProcess();
    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool DuplicateHandle(SafeProcessHandle source, IntPtr handle, IntPtr target,
        out SafeFileHandle duplicate, uint access, bool inherit, uint options);
    [DllImport("kernel32.dll")]
    private static extern uint GetFileType(SafeFileHandle file);
    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern uint GetFinalPathNameByHandle(SafeFileHandle file, StringBuilder path, uint size, uint flags);
    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool GetFileSizeEx(SafeFileHandle file, out long size);
    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern SafeFileHandle CreateFileMapping(SafeFileHandle file, IntPtr attributes, uint protection,
        uint sizeHigh, uint sizeLow, string? name);
    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern IntPtr MapViewOfFile(SafeFileHandle mapping, uint access, uint offsetHigh, uint offsetLow, nuint size);
    [DllImport("kernel32.dll")]
    private static extern bool UnmapViewOfFile(IntPtr view);
}
