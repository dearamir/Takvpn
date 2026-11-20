using System.IO;
using System.Windows;
using System.Windows.Threading;

namespace TakVPN.Windows;

public partial class App : Application
{
    protected override void OnStartup(StartupEventArgs e)
    {
        DispatcherUnhandledException += OnDispatcherUnhandledException;
        AppDomain.CurrentDomain.UnhandledException += (_, args) =>
            WriteCrashLog(args.ExceptionObject as Exception ?? new Exception("Unknown unhandled exception."));
        TaskScheduler.UnobservedTaskException += (_, args) =>
        {
            WriteCrashLog(args.Exception);
            args.SetObserved();
        };
        base.OnStartup(e);
    }

    private static void OnDispatcherUnhandledException(object sender, DispatcherUnhandledExceptionEventArgs e)
    {
        WriteCrashLog(e.Exception);
        e.Handled = true;
        try
        {
            MessageBox.Show(
                "یک خطای پیش‌بینی‌نشده رخ داد و برنامه برای جلوگیری از بسته‌شدن ناگهانی آن را ثبت کرد.\n" +
                "گزارش خطا در پوشهٔ LocalAppData\\TakVPN\\logs\\crash.log ذخیره شد.\n\n" +
                e.Exception.Message,
                "TakVPN - خطا",
                MessageBoxButton.OK,
                MessageBoxImage.Error);
        }
        catch { }
    }

    private static void WriteCrashLog(Exception exception)
    {
        try
        {
            var directory = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "TakVPN", "logs");
            Directory.CreateDirectory(directory);
            File.AppendAllText(
                Path.Combine(directory, "crash.log"),
                $"[{DateTimeOffset.Now:O}] {exception}\n{new string('-', 72)}\n");
        }
        catch { }
    }
}
