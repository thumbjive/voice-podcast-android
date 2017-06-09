package com.thumbjive.atennapod;

import android.os.AsyncTask;
import android.util.Log;

import java.io.File;
import java.io.IOException;

import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.core.service.playback.PlayerStatus;
import de.danoeh.antennapod.core.util.playback.PlaybackController;
import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;
import edu.cmu.pocketsphinx.SpeechRecognizerSetup;

/**
 * Experimental support for play/stop voice commands
 *
 * Derived from:
 *   https://github.com/cmusphinx/pocketsphinx-android-demo
 */

public class VoiceControlService
        implements RecognitionListener {

    private SpeechRecognizer recognizer;
    private static final String KWS_SEARCH = "wakeup";
    private static final String KEYPHRASE = "wakeup";
    private static final String COMMANDS_SEARCH = "commands";

    private String currentSearch = KWS_SEARCH;

    MainActivity mainActivity;

    public VoiceControlService(MainActivity mainActivity) {
        this.mainActivity = mainActivity;
    }

    public void run() {
        Log.i("TJ", "runRecognizerSetup");
        // Recognizer initialization is a time-consuming and it involves IO,
        // so we execute it in async task
        new AsyncTask<Void, Void, Exception>() {
            @Override
            protected Exception doInBackground(Void... params) {
                try {
                    Log.i("TJ", "about to load assets");
                    Assets assets = new Assets(mainActivity);
                    File assetDir = assets.syncAssets();
                    Log.i("TJ", "after syncAssets");
                    setupRecognizer(assetDir);
                } catch (IOException e) {
                    Log.i("TJ", "asset load error: " + e);
                    return e;
                }
                return null;
            }

            @Override
            protected void onPostExecute(Exception result) {
                if (result != null) {
                    showToast("Failed to init recognizer " + result);
                } else {
                    showToast("Speech recognizer initialized");
                    switchSearch(KWS_SEARCH);
                }
            }
        }.execute();
    }

    private void switchSearch(String searchName) {
        Log.i("TJ", "switchSearch: " + searchName);
        currentSearch = searchName;

        if (currentSearch.startsWith(KWS_SEARCH)) {
            showToast("Say \"" + KEYPHRASE + "\" to wake up");
        } else if (currentSearch.startsWith(COMMANDS_SEARCH)) {
            showToast("Say \"play\", \"pause\", or \"sleep\"");
        }

        restartRecognizer();
    }

    private void restartRecognizer() {
        Log.i("TJ", "restartRecognizer, currentSearch: " + currentSearch);
        recognizer.stop();

        // If we are not spotting, start listening with timeout (10000 ms or 10 seconds).
        if (currentSearch.equals(KWS_SEARCH))
            recognizer.startListening(currentSearch);
        else
            recognizer.startListening(currentSearch, 30000);
    }

    private void setupRecognizer(File assetsDir) throws IOException {
        // The recognizer can be configured to perform multiple searches
        // of different kind and switch between them

        recognizer = SpeechRecognizerSetup.defaultSetup()
                .setAcousticModel(new File(assetsDir, "en-us-ptm"))
                .setDictionary(new File(assetsDir, "cmudict-en-us.dict"))

                .setRawLogDir(assetsDir) // To disable logging of raw audio comment out this call (takes a lot of space on the device)

                .getRecognizer();
        Log.i("TJ", "recognizer instantiated");
        recognizer.addListener(this);

        // Create keyword-activation search.
        recognizer.addKeyphraseSearch(KWS_SEARCH, KEYPHRASE);

        // voice control commands
        File commandsGrammar = new File(assetsDir, "commands.gram");
        recognizer.addGrammarSearch(COMMANDS_SEARCH, commandsGrammar);
        Log.i("TJ", "grammer added");
    }


    /**
     * In partial result we get quick updates about current hypothesis. In
     * keyword spotting mode we can react here, in other modes we need to wait
     * for final result in onResult.
     */
    @Override
    public void onPartialResult(Hypothesis hypothesis) {
        if (hypothesis == null)
            return;

        String text = hypothesis.getHypstr();
        Log.i("TJ", "hypothesis: " + text);
    }

    /**
     * This callback is called when we stop the recognizer.
     */
    @Override
    public void onResult(Hypothesis hypothesis) {
        if (hypothesis != null) {
            String text = hypothesis.getHypstr();
            Log.i("TJ", "onResult: " + text);
            if (text.equals(KEYPHRASE)) {
                switchSearch(COMMANDS_SEARCH);
            } else if ("play".equals(text)) {
                handlePlayCommand();
            } else if ("pause".equals(text) || "stop".equals(text)) {
                handlePauseCommand();
            } else if ("sleep".equals(text)) {
                switchSearch(KWS_SEARCH);
            }
        }
    }

    private void handlePlayCommand() {
        PlayerStatus status = getPlaybackController().getStatus();
        Log.i("TJ", "handlePlay - status: " + status);
        if (status != PlayerStatus.PLAYING) {
            Log.i("TJ", "playing...");
            getPlaybackController().playPause();
        }
    }

    private void handlePauseCommand() {
        PlayerStatus status = getPlaybackController().getStatus();
        Log.i("TJ", "handlePlay - status: " + status);
        if (status == PlayerStatus.PLAYING) {
            Log.i("TJ", "pausing...");
            getPlaybackController().playPause();
        }
    }

    @Override
    public void onBeginningOfSpeech() {
    }

    @Override
    public void onEndOfSpeech() {
        Log.i("TJ", "onEndOfSpeech");
        restartRecognizer();
    }

    @Override
    public void onError(Exception error) {
        Log.e("TJ", "onError: " + error.getMessage());
        showToast(error.getMessage());
    }

    @Override
    public void onTimeout() {
        switchSearch(KWS_SEARCH);
    }

    //
    // MainActivity hooks
    //

    public PlaybackController getPlaybackController() {
        return mainActivity.getPlaybackController();
    }

    private void showToast(String text) {
        mainActivity.showToast(text);
    }
}
