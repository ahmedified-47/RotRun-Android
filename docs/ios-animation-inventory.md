# iOS animation inventory

No animation sets response/dampingFraction. Springs use duration and, when written, bounce (default 0). .snappy extraBounce 0. Reduce Motion, where checked, shortens/removes motion (noted per item).

## Shared motion
### Screen slide
- Screens: pushed pages, onboarding steps, intervention covers (Standard Dialogue, Brain Call, Quick Check, Breathing, friend cameo, Rot cameo menu, audio slap).
- New screen slides in from trailing edge; back: incoming from leading, outgoing to trailing. 0.28s ease-in-out. Reduce Motion: 0.01s ease-out + opacity instead of move.
- Tabs (Home, Block, Stats, Settings) and Focus popup do NOT use this. Opening Rot chat (Talk) has NO slide (instant push/pop).
### Intervention appear (`interventionAppear`)
- Used by onboarding, paywall copy, leaderboard, interventions, bailout banner. Opacity 0->1 + 14pt rise, 0.48s ease-out, with caller delay; once. Reduce Motion: snap visible.
- InterventionMotion.stagger = 0.42s ease-out; progress = 0.55s ease-in-out.
### Mascot idle breath
- Any CartoonBrainView >=96pt with animated on (onboarding, home, Rot chat large header, intervention mascots). Scale 1->1.03, 2.4s ease-in-out, autoreverse, forever. Reduce Motion or animated:false: none. <96pt: still.
### Pixel brain pulse
- PixelBrainView (rank brain canvas). Frame at most every 0.08s; glow opacity = 0.5+0.5*sin(t*speed); speed 1.2 Rotter, 0.6 Sloth, 2.0 Active, 3.2 Cosmic Mind rad/s. Rotter also jitters some cells down 0.15 cell. Forever, Reduce Motion not checked.
### Chunky button press (GamePressButtonStyle)
- Intervention game buttons. Pressed: scale 0.97, offset y +3pt. spring(duration 0.16, bounce 0.25).

## Onboarding
- Step change and onboarding->main handoff: screen slide.
- interventionAppear delays: Quiz choice rows 0,0.06,0.10,0.14,0.18,0.22; Hours question 0,0.06,0.10,0.14; Setup intro 0,0.06, continue 0.18; Notifications cards 0.08,0.12,0.16,0.20; Goal 0; Later setup cards 0,0.08,0.12,0.14,0.16,0.20; Story carousel page 0, dots 0.08; Hold to ascend 0,0.06,0.10,0.18.
- Hours number (quiz + goal): numeric text content transition, snappy 0.2s.
- Choice highlight: fill/stroke + scale 1->1.015 on selected card, 0.2s ease-in-out (peak-focus group animates stroke only).
- Continue button: opacity 1 or 0.45 on enabled, spinner on loading, 0.22s ease-in-out each.
- Quiz progress bar: width, 0.55s ease-in-out on appear and each step. Reduce Motion: immediate.
- Story metric bars: fill 0->target, 0.55s ease-in-out, delay 0.18s, once. Reduce Motion: jump.
- Personalizing checklist: progress steps 0.35,0.62,0.90,1.0, each 0.55s ease-in-out, rows' checkmark/color 0.42s ease-out; 0.55s wait between steps (0.18 RM); advance after another 0.35s (0.12 RM). Row anim removed under RM.
- Hold to ascend: hold up to 1.1s, ring trim follows progress linear 1.1s; meditating brain rises 48pt and grows 10% tied to progress; release early -> progress to 0 in 0.2s ease-out (0.01 RM).
- Ascend diamond pulse: white diamond circle scale 1->1.06, 1.15s ease-in-out autoreverse forever while progress <0.02. RM: none.
- Story carousel dots: active 8pt dot blue, 0.2s ease-in-out.

## Home
- Bottom tabs: NO animation (explicitly off).
- Brain health bar (hero card): capsule width 0.55s ease-in-out on change.
- Home mascot = large CartoonBrainView idle breath; no separate entrance.
- Focus chooser and running focus session overlays: opacity fade (no slide), spring 0.35s bounce 0. Countdown label updates once/sec, not animated.
- Bailout banner: interventionAppear delay 0; stays 4s; cleared with no exit animation.
- Toast (XP/unlock): appears, cleared after 2.2s, no transition.

## Rot chat
- Scroll to latest: 0.25s ease-out on transcript/voice transcript change or field focus; first appear not animated.
- Listening pulse: 28pt brain next to Rot title scales 1->1.16, 0.75s ease-in-out autoreverse forever while voice call active (large idle breath off on that image). Stop: no animation. RM: no pulse.
- Working status caption: words swap every 1.6s ("…chipping away", "…working on it", "…thinking", "…looking it up", "…sorting it out"), no fade.

## Shield and interventions
- Shield screen itself static. Each intervention cover enters with screen slide (Focus fades instead).
### Standard Dialogue
- Shared appear on first content. New bubble insert: scale from 0.86 anchored at bottom corner (trailing for user, leading for brain) + opacity; removal opacity. Brain lines spring 0.36 bounce 0.18; user lines spring 0.32 bounce 0.12. Scroll 0.25s ease-out. Yes/no, duration, confirm beats: spring 0.38.
- Pauses: 0.38s before first line, 0.52s between intro lines, 0.42s before friend's question, 0.28s before yes/no, 0.40s after "nah", 0.85s before dismiss, 0.45/0.48/0.25s through yes path, 0.42/0.28s on duration path.
### Brain Call (FaceTime-style)
- Incoming ring: outer circle scale 1->1.08, 1.15s ease-in-out autoreverse forever. Incoming copy interventionAppear at 0,0.08,0.16,0.22. Accept -> question with animation OFF.
- Question screen appear 0,0.08,0.16. "Yes, I really do" reveals duration choices: opacity + 12pt up, 0.42s ease-out; tiles at 0,0.05,0.10. Camera preview no animation.
### Quick Check
- Appear: header 0, bubble 0.06, mascot 0.10. "Yes": bubble and mascot move in place (shared element / matchedGeometry), bottom controls swap; spring 0.45 bounce 0.12; container step change 0.55s ease-in-out. Controls enter from 12pt below; step 1 leaves up 8pt; step 2 leaves down 8pt.
### Breathing
- Ring + meditating brain scale together: inhale 1->1.18 over 4s, hold 2s, exhale ->1 over 4s, ease-in-out. Phase title crossfade 0.35s; countdown numeric 0.2s. After one cycle question replaces ring in 0.45s; "Yes" reveals durations 0.35s. Question appear 0,0.08,0.16; tiles 0,0.04,0.08.
- Haptics: inhale light intensity 0.72 twice/sec for 4s; hold start medium 1.0 once; exhale soft 0.55 twice/sec for 4s.
### Friend cameo / audio slap / Rot cameo menu
- Screen slide entry. Friend cameo appear 0,0.06,0.10,0.14,0.16, buttons 0.20,0.26. Continue -> duration step spring 0.4 bounce 0.1.
### Bail out (ransom sheet)
- Robed brain scale 1.08 while finger down, 0.4s ease-in-out (RM: 1). Ring progress steps every 50ms for 8s. Completion: one medium impact haptic.

## Paywall
- Onboarding paywall copy interventionAppear 0,0.08,0.14. Paywall views/sheets otherwise no custom animation (system sheet).

## Leaderboard
- Trophy icon bounce once on header appear (skip under RM). Header, subtitle, scope toggle appear 0,0.06,0.10. Podium 0.04, 0.08; rows 0.12 + min(index,7)*0.05. Empty/signed-out states 0,0.08,0.12,0.18 (+0.22). Daily/Friends toggle: blue capsule slides, spring 0.32 (RM: 0.12s ease-out).

## Widgets: none. XP/level-up: none. Confetti: none. Other haptics: none.
