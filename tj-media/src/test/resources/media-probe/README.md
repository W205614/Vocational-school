These tiny fixtures are generated for this project's metadata tests, not course content.

- `tiny.mp4`: FFmpeg blue 160x90 / 25 fps H.264 recording, 1.28 seconds.
- `recorder.webm`: a real browser canvas / MediaRecorder VP8 recording, remuxed
  with `ffmpeg -i input.webm -c copy -f webm -live 1 pipe:1` so the container has
  no Duration field. The native test asserts that precondition before testing
  the packet-timestamp fallback. It contains approximately six seconds of frames.
