/* ====================================================================
 * Copyright (c) 2014 Alpha Cephei Inc.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY ALPHA CEPHEI INC. ``AS IS'' AND
 * ANY EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL CARNEGIE MELLON UNIVERSITY
 * NOR ITS EMPLOYEES BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * ====================================================================
 */

package edu.cmu.pocketsphinx.demo;

import static edu.cmu.pocketsphinx.SpeechRecognizerSetup.defaultSetup;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;

public class PocketSphinxActivity implements
        RecognitionListener {
		
    /* Named searches allow to quickly reconfigure the decoder */
    private static final String KWS_SEARCH = "wakeup";
    private static final String DIGITS_SEARCH = "digits";
    private static final String MENU_SEARCH = "menu";
    private String keyWordResult="";
    private boolean update = false;
    private final Object lock = new Object();
    Set<String> valSet = new HashSet<>();
    private Pingpong game;
    private TextToSpeech t1;
    private Context context;
    private int keywordPreposition = -1;


    private static final String KEYPHRASE = "start game";

    private SpeechRecognizer recognizer;

    public PocketSphinxActivity(Context context){
        this.context = context;
    }


    public void onCreate() {
        game = new Pingpong("pointwhite", "pointblack", t1);
        // Prepare the data for UI

        new AsyncTask<Void, Void, Exception>() {
            @Override
            protected Exception doInBackground(Void... params) {
                try {
                    Assets assets = new Assets(context);
                    File assetDir = assets.syncAssets();
                    setupRecognizer(assetDir);
                } catch (IOException e) {
                    return e;
                }
                return null;
            }

            @Override
            protected void onPostExecute(Exception result) {
                if (result == null) {
                    switchSearch(KWS_SEARCH);
                }
            }
        }.execute();

    }

    public void onDestroy() {
        recognizer.cancel();
        recognizer.shutdown();
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
        if (text.equals(KEYPHRASE)) {
            switchSearch(DIGITS_SEARCH);
            System.out.println("Game Start!!!");
            game.serve();
        }
        else
        {
            if (!game.getDone()) {
                getKeyword(text);
                System.out.println(text);
                if (keyWordResult != null && !keyWordResult.equals("")) {
                    if (update) {
                        game.gameUpdate(keyWordResult);
                        update = false;
                    }
                }
            }
            else
            {
                game.winingMessage();
                recognizer.shutdown();
                // exit app TODO
            }
        }

    }

    /**
     * This callback is called when we stop the recognizer.
     */
    @Override
    public void onResult(Hypothesis hypothesis) {
    }

    @Override
    public void onBeginningOfSpeech() {
    }

    /**
     * We stop recognizer here to get a final result
     */
    @Override
    public void onEndOfSpeech() {
    }

    private void switchSearch(String searchName) {
        recognizer.stop();
        
        // If we are not spotting, start listening with timeout (10000 ms or 10 seconds).
        if (searchName.equals(KWS_SEARCH))
            recognizer.startListening(searchName);
            //recognizer.startListening("what");
        else
            //recognizer.startListening("what",10000);
            recognizer.startListening(searchName, 100000);
    }

    private void setupRecognizer(File assetsDir) throws IOException {
        // The recognizer can be configured to perform multiple searches
        // of different kind and switch between them
        
        recognizer = defaultSetup()
                .setAcousticModel(new File(assetsDir, "en-us-ptm"))
                .setDictionary(new File(assetsDir, "cmudict-en-us.dict"))
                
                // To disable logging of raw audio comment out this call (takes a lot of space on the device)
                .setRawLogDir(assetsDir)

                // Threshold to tune for keyphrase to balance between false alarms and misses
                .setKeywordThreshold(1e-45f)
                
                // Use context-independent phonetic search, context-dependent is too slow for mobile
                .setBoolean("-allphone_ci", true)
                
                .getRecognizer();

        recognizer.addListener(this);

        // Create keyword-activation search.
        recognizer.addKeyphraseSearch(KWS_SEARCH, KEYPHRASE);

        File menuGrammar = new File(assetsDir, "menu.gram");
        recognizer.addGrammarSearch(MENU_SEARCH, menuGrammar);

        // Create grammar-based search for digit recognition
        File digitsGrammar = new File(assetsDir, "digits.gram");
        //recognizer.addKeywordSearch(DIGITS_SEARCH, digitsGrammar);
        recognizer.addGrammarSearch(DIGITS_SEARCH, digitsGrammar);
        initValSet();
    }

    @Override
    public void onError(Exception error) {
    }

    private void initValSet(){
        valSet.add("rematch");
        valSet.add("score");
        valSet.add("serve");
        valSet.add("wrong");
        valSet.add("pointblack");
        valSet.add("pointwhite");
    }

    private synchronized void getKeyword(String s){
        keyWordResult = null;
        String[] a = s.split(" ");
        for (int i = 0;i<a.length;i++){
            if (valSet.contains(a[i]) && keywordPreposition < i){
                keyWordResult = a[i];
                keywordPreposition = i;
                update = true;
            }
        }
    }

    public void addName() throws IOException {
//        Button button = (Button) findViewById(R.id.button);
//        button.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                recognizer.shutdown();
//                SaveName sn = new SaveName();
//                try {
//                    File file = assets.syncAssets();
//                    File digitsGrammar = new File(file, "digits.gram");
//                    //recognizer.addKeywordSearch(DIGITS_SEARCH, digitsGrammar);
////                    recognizer.addGrammarSearch(DIGITS_SEARCH, digitsGrammar);
//                    sn.writeName("mary",digitsGrammar);
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//                Bundle state = null;
//                onCreate(state);
//            }
//        });
    }

    @Override
    public void onTimeout() {
        switchSearch(KWS_SEARCH);
    }
}
