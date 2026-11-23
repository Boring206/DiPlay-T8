package com.shilapi.xcertplay

import androidx.media3.common.PlaybackException
import com.shilapi.xcertplay.airplay.VideoInCar
import com.shilapi.xcertplay.host.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// #1180: the player said "protected video" when CarPlay's reconnect broke the stream (a network error).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class CarPlayVideoFailureTest {
    @Test fun onlyDrmErrorsNameProtectedVideo() {
        assertFailure(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, VideoInCar.ERROR_NETWORK, R.string.video_cannot_load)
        assertFailure(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, VideoInCar.ERROR_NETWORK, R.string.video_cannot_load)
        assertFailure(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, VideoInCar.ERROR_DECODER, R.string.video_cannot_play_here)
        assertFailure(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED, VideoInCar.ERROR_DECODER, R.string.video_cannot_play_here)
        assertFailure(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED, VideoInCar.ERROR_INCOMPATIBLE_ASSET, R.string.video_cannot_play_here)
        assertFailure(PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED, VideoInCar.ERROR_INCOMPATIBLE_ASSET, R.string.video_cannot_play_here)
        assertFailure(PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED, VideoInCar.ERROR_INCOMPATIBLE_ASSET, R.string.video_cannot_play)
        assertFailure(PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED, VideoInCar.ERROR_INCOMPATIBLE_ASSET, R.string.video_cannot_play)
    }

    // An app's own URL loads through the iPhone, whose failures are plain IOExceptions (2000): the text must
    // not send the driver to the head unit's internet.
    @Test fun anUnspecifiedIoErrorSaysTheVideoStoppedLoading() {
        assertFailure(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, VideoInCar.ERROR_NETWORK, R.string.video_cannot_load)
    }

    // A live stream fallen behind, a timeout, audio output or an unknown error is neither the format nor DRM.
    @Test fun errorsWithoutAKnownCauseStateNone() {
        for (code in listOf(
            PlaybackException.ERROR_CODE_UNSPECIFIED,
            PlaybackException.ERROR_CODE_REMOTE_ERROR,
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW,
            PlaybackException.ERROR_CODE_TIMEOUT,
            PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
            PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSOR_INIT_FAILED,
        )) {
            // The iPhone is told what it was told before.
            assertFailure(code, VideoInCar.ERROR_INCOMPATIBLE_ASSET, R.string.video_cannot_play_on_car)
        }
    }

    private fun assertFailure(playbackErrorCode: Int, iphoneCode: Int, message: Int) {
        assertEquals(CarPlayVideo.PlayerFailure(iphoneCode, message), CarPlayVideo.failureFor(playbackErrorCode))
    }
}
