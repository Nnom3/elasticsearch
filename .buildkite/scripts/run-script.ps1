Add-Type -TypeDefinition @'
using Microsoft.Win32.SafeHandles;
using System;
using System.ComponentModel;
using System.Runtime.InteropServices;

public class NativeMethods
{
    public enum JOBOBJECTINFOCLASS
    {
        AssociateCompletionPortInformation = 7,
        BasicLimitInformation = 2,
        BasicUIRestrictions = 4,
        EndOfJobTimeInformation = 6,
        ExtendedLimitInformation = 9,
        SecurityLimitInformation = 5,
        GroupInformation = 11
    }

    [StructLayout(LayoutKind.Sequential)]
    struct JOBOBJECT_BASIC_LIMIT_INFORMATION
    {
        public Int64 PerProcessUserTimeLimit;
        public Int64 PerJobUserTimeLimit;
        public UInt32 LimitFlags;
        public UIntPtr MinimumWorkingSetSize;
        public UIntPtr MaximumWorkingSetSize;
        public UInt32 ActiveProcessLimit;
        public Int64 Affinity;
        public UInt32 PriorityClass;
        public UInt32 SchedulingClass;
    }


    [StructLayout(LayoutKind.Sequential)]
    struct IO_COUNTERS
    {
        public UInt64 ReadOperationCount;
        public UInt64 WriteOperationCount;
        public UInt64 OtherOperationCount;
        public UInt64 ReadTransferCount;
        public UInt64 WriteTransferCount;
        public UInt64 OtherTransferCount;
    }

    [StructLayout(LayoutKind.Sequential)]
    struct JOBOBJECT_EXTENDED_LIMIT_INFORMATION
    {
        public JOBOBJECT_BASIC_LIMIT_INFORMATION BasicLimitInformation;
        public IO_COUNTERS IoInfo;
        public UIntPtr ProcessMemoryLimit;
        public UIntPtr JobMemoryLimit;
        public UIntPtr PeakProcessMemoryUsed;
        public UIntPtr PeakJobMemoryUsed;
    }

    [DllImport("Kernel32.dll", EntryPoint = "AssignProcessToJobObject", SetLastError = true)]
    private static extern bool NativeAssignProcessToJobObject(
        SafeHandle hJob,
        SafeHandle hProcess
    );

    public static void AssignProcessToJobObject(SafeHandle job, SafeHandle process)
    {
        if (!NativeAssignProcessToJobObject(job, process))
            throw new Win32Exception();
    }

    [DllImport("Kernel32.dll", CharSet = CharSet.Unicode, EntryPoint = "CreateJobObjectW", SetLastError = true)]
    private static extern SafeFileHandle NativeCreateJobObjectW(
        IntPtr lpJobAttributes,
        string lpName
    );

    [DllImport("Kernel32.dll", EntryPoint = "CloseHandle", SetLastError = true)]
    private static extern bool NativeCloseHandle(
        SafeHandle hJob
    );

    [DllImport("kernel32.dll")]
    public static extern bool SetInformationJobObject(
        SafeHandle hJob, JOBOBJECTINFOCLASS JobObjectInfoClass,
        IntPtr lpJobObjectInfo, uint cbJobObjectInfoLength);

    private const UInt32 JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE = 0x2000;

    public static SafeHandle CreateJobObjectW(string name)
    {
        SafeHandle job = NativeCreateJobObjectW(IntPtr.Zero, name);

        JOBOBJECT_BASIC_LIMIT_INFORMATION info =
            new JOBOBJECT_BASIC_LIMIT_INFORMATION();
        info.LimitFlags = JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;

        JOBOBJECT_EXTENDED_LIMIT_INFORMATION extendedInfo =
            new JOBOBJECT_EXTENDED_LIMIT_INFORMATION();
        extendedInfo.BasicLimitInformation = info;

        int length = Marshal.SizeOf(
            typeof(JOBOBJECT_EXTENDED_LIMIT_INFORMATION));
        IntPtr extendedInfoPtr = Marshal.AllocHGlobal(length);
        Marshal.StructureToPtr(extendedInfo, extendedInfoPtr, false);

        SetInformationJobObject(job,
                                JOBOBJECTINFOCLASS.ExtendedLimitInformation,
                                extendedInfoPtr,
                                (uint)length);

        if (job.IsInvalid)
            throw new Win32Exception();

        return job;
    }

    public static void CloseHandle(SafeHandle job)
    {
        if (!NativeCloseHandle(job))
            throw new Win32Exception();
    }
}
'@

$guid = [guid]::NewGuid().Guid
Write-Output "Creating job object with name $guid"
$job = [NativeMethods]::CreateJobObjectW($guid)
$process = Start-Process -PassThru powershell -ArgumentList @args
[NativeMethods]::AssignProcessToJobObject($job, $process.SafeHandle)

try {
  Write-Output "Waiting for process $($process.Id) to complete..."
  Wait-Process -Id $process.Id
  Write-Output "Process finished with exit code $($process.ExitCode), terminating job and exiting..."
} finally {
  [NativeMethods]::CloseHandle($job)
  exit $process.ExitCode
}
