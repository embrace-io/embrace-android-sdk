## Smoothness Vital

The smoothness is a measure of frame rate during interactions. Times when the user is most aware of frames being dropped or blocked
leading to a poor user experience as the app "stutters" or "glitches". Smoothness does not include idle times and vsyncs as frame drops
on static screens have no visible impact to the user. Similarly, an animation on a screen that the user isn't actively interacting with
may be noticed by the user but does not typically mean a worse user experience.

To report the "smoothness" of the app all frames that result from interactions are tracked and then aggregated into a "normalized FPS"
number. The reported value always assumes a refresh rate of 60fps as this is easy to communicate and has enormous mind-share. As such
the values are floating points, since we must be able to track "half" and "quarter" frames. If the screens actual refresh rate is 120Hz
then a single frame dropped is counted as 0.5 frames. Conversely, a single dropped frame on a display running at 30Hz is counted as 2.0
frames dropped. This accumulates over time. Only the portion of a frame that overruns its render budget counts as dropped, so a frame
that runs a full second past its budget adds 60 dropped frames (one second divided by the 16.67ms 60fps reference frame), independent of
the display's refresh rate.
