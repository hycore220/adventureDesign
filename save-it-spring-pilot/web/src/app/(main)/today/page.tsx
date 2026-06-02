import { AppHeader } from "@/components/shell/app-header";
import { TodayReminderSection } from "@/components/today/today-reminder-section";

export default function TodayPage() {
  return (
    <>
      <AppHeader title="오늘" />
      <div className="p-4">
        <TodayReminderSection />
      </div>
    </>
  );
}
