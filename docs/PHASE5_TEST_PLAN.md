# Phase 5 manual test plan

Nothing in phase 5 has been run yet. Fill in the result column on a real phone (Android 10 or newer, ideally also 14).
"Pass" means it behaved exactly as written. Write what you saw for anything else.

| # | Test | Steps | Expected | Result |
|---|---|---|---|---|
| 1 | Overlay permission | Floating panel > switch on without the permission | Switch is disabled, a grant button opens the system page | |
| 2 | Start assistant | Grant overlay, switch on | Notification appears, handle shows on the edge | |
| 3 | Handle drag | Drag the handle up/down, then across the screen | Follows the finger, snaps to nearest edge, position kept after closing the app | |
| 4 | Panel open/close | Tap the handle, then tap the game or empty screen outside | Panel opens with animation, closes on outside tap, game receives that tap | |
| 5 | Rotation | Open panel, rotate | Panel stays on screen, handle keeps its relative height | |
| 6 | Game detection | Grant Usage access, set "Only in games", turn the game's Floating panel switch on, open the game | Handle appears only in that game; Home shows the game as running | |
| 7 | Session record | Play 2 minutes, go Home for 10 s, come back, quit, wait 40 s | One session, about 2 min+ in Statistics | |
| 8 | Profile apply | Set Power saving (brightness 40%), open the game | Brightness drops; Control tab lists what changed | |
| 9 | Profile restore | Leave the game and wait 40 s | Brightness, DND and volume return to what they were | |
| 10 | Crash recovery | During a game, force-stop Game Master, reopen it, start the assistant | Original settings restored, open session closed | |
| 11 | Missing permission | Revoke Modify system settings, start a game with a brightness profile | Brightness skipped, panel says why, nothing crashes | |
| 12 | DND | Quick tab > Do Not Disturb with and without access | Toggles with access; opens the grant page without | |
| 13 | Volume and brightness sliders | Control tab | System values change live | |
| 14 | Dock | Add 9 apps, try a 10th, add a duplicate, drag to reorder, remove one, reboot | Refuses 10th and duplicate, order and content kept, tap launches the app | |
| 15 | Screenshot | Enable the service (Allow restricted settings if needed), tap Screenshot | System screenshot taken, panel not in the picture | |
| 16 | Screen recording | Tap Record, confirm, play, tap Stop | MP4 in Movies/GameMaster, plays, no sound | |
| 17 | Network test | Quick tab > Network test | Average, jitter and lost percentage appear | |
| 18 | FPS | Grant DUMP over ADB, open a game, Monitor tab | Number appears; without the grant a clear message | |
| 19 | Panel editor | Change side, size, opacity, widgets, shortcut order while running | Panel changes immediately and survives restart | |
| 20 | Statistics | Open with and without sessions, change range, clear | Correct numbers, empty state, clear asks for confirmation | |
| 21 | Reboot | Switch on "Start after reboot", reboot | Assistant starts again; nothing starts when it was off | |
| 22 | Migration | Install phase 4, add games, update to phase 5 | Library and profiles intact, new screens work | |
| 23 | Battery | Run the assistant for 30 minutes idle and in a game | Note battery use; no runaway CPU | |
| 24 | Game compatibility | Try 3 different games, one in immersive full screen | Handle visible, game still receives touches elsewhere | |
