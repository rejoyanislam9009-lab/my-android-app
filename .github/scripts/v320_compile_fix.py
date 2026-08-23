from pathlib import Path

p = Path('app/src/main/java/com/guide/app/Reminders.kt')
s = p.read_text()

bad = r'''        if (intent.action == ReminderScheduler.ACTION_DISABLE_REMINDER) {
            GuideAlarmService.stop(context.applicationContext)
            GuideVoicePrompt.stop()
            AlarmSoundPlayer.stop()
            manager.cancel(key.hashCode())
            ReminderScheduler.disableReminder(context.applicationContext, key)
            return
        }
'''

# v3.20 initially anchored to the first NotificationManager declaration in the
# file, which can live inside ReminderScheduler rather than ReminderReceiver.
# Remove that misplaced copy, then insert the handler in the receiver where
# intent and key are actually in scope.
if bad in s:
    s = s.replace(bad, '', 1)

receiver_start = s.find('class ReminderReceiver : BroadcastReceiver() {')
if receiver_start < 0:
    raise SystemExit('ReminderReceiver not found')

manager_line = '        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager\n'
manager_pos = s.find(manager_line, receiver_start)
if manager_pos < 0:
    raise SystemExit('ReminderReceiver NotificationManager anchor not found')
insert_at = manager_pos + len(manager_line)

if bad not in s[receiver_start:]:
    s = s[:insert_at] + bad + s[insert_at:]

p.write_text(s)
print('v3.20 compile fix: disable-reminder action moved into ReminderReceiver scope')
