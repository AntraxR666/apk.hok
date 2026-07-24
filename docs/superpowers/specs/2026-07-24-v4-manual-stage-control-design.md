# V4 Manual Stage Control Design

## Goal

Keep the overlay session active while the user explicitly controls whether the assistant is paused, analyzing the hero draft, or presenting the in-game plan.

## State machine

`PAUSED -> DRAFT -> IN_GAME`, with transitions in either direction controlled by the user. Visual classification may produce a suggested transition, but it never changes the selected state automatically.

## Behavior

- `PAUSED`: retain projection authorization and previous composition; detach the capture surface and process no frames.
- `DRAFT`: attach the capture surface; run OCR, portrait matching, slot stabilization, recommendation and draft strategy.
- `IN_GAME`: detach the capture surface; retain the final composition and show the final strategy.

## UI

The floating panel and MainActivity expose Selection, Game and Pause buttons. A contextual confirmation button appears when the detected screen disagrees with the selected stage.

## Safety and performance

No captures are stored. Detaching the VirtualDisplay surface outside draft mode reduces image processing and avoids the overlay wasting battery while the user is playing.
