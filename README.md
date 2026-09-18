# TwoPersonMessenger Android V5

V5 redesign: full-height dark messenger UI, safe top/bottom layout, vector icons for back/video/call/menu/emoji/attachment/send, large categorized emoji panel, keyboard↔emoji switching, improved message bubbles/time/read status, settings menu, local clear, and existing hidden Documents gateway/server chat behavior.

Important: audio/video call screens are present, but true device-to-device WebRTC media still requires signaling + STUN/TURN server work. Attachment button is UI-ready but file upload requires server endpoint support.

V5.1 fixes:
- Auto-clear OFF now cancels any pending background timer.
- Auto-clear cutoff uses the time the app entered background, so messages received while backgrounded are not accidentally hidden.
- Returning to the app after it was backgrounded routes to Documents instead of exposing the chat screen.
