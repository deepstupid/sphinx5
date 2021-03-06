/*
 * Copyright 2014 Alpha Cephei Inc.
 * All Rights Reserved.  Use is subject to license terms.
 *
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 */

package edu.cmu.sphinx.api;

import edu.cmu.sphinx.alignment.LongTextAligner;
import edu.cmu.sphinx.alignment.SimpleTokenizer;
import edu.cmu.sphinx.alignment.TextTokenizer;
import edu.cmu.sphinx.linguist.language.grammar.AlignerGrammar;
import edu.cmu.sphinx.linguist.language.ngram.DynamicTrigramModel;
import edu.cmu.sphinx.recognizer.Recognizer;
import edu.cmu.sphinx.result.Result;
import edu.cmu.sphinx.result.WordResult;
import edu.cmu.sphinx.util.Range;
import edu.cmu.sphinx.util.TimeFrame;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.logging.Logger;

public class SpeechAligner {
    private final Logger logger = Logger.getLogger(getClass().getSimpleName());

    private static final int TUPLE_SIZE = 3;

    private final Context context;
    private final Recognizer recognizer;
    private final AlignerGrammar grammar;
    private final DynamicTrigramModel languageModel;

    private TextTokenizer tokenizer;

    public SpeechAligner(String amPath, String dictPath, String g2pPath) throws IOException {
        Configuration configuration = new Configuration();
        configuration.setAcousticModelPath(amPath);
        configuration.setDictionaryPath(dictPath);

        context = new Context(configuration);
        if (g2pPath != null) {
            context.setLocalProperty("dictionary->g2pModelPath", g2pPath);
            context.setLocalProperty("dictionary->g2pMaxPron", "2");
        }
        context.setLocalProperty("lexTreeLinguist->languageModel", "dynamicTrigramModel");
        recognizer = context.getInstance(Recognizer.class);
        grammar = context.getInstance(AlignerGrammar.class);
        languageModel = context.getInstance(DynamicTrigramModel.class);
        setTokenizer(new SimpleTokenizer());
    }

    public List<WordResult> align(URL audioUrl, String transcript) throws IOException {
        return align(audioUrl, tokenizer.expand(transcript));
    }

    /**
     * Align audio to sentence transcript
     * 
     * @param audioUrl audio file URL to process
     * @param sentenceTranscript cleaned transcript
     * @return List of aligned words with timings
     * @throws IOException if IO went wrong
     */
    public List<WordResult> align(URL audioUrl, List<String> sentenceTranscript) throws IOException {
        
        List<String> transcript = sentenceToWords(sentenceTranscript);

        LongTextAligner aligner = new LongTextAligner(transcript, TUPLE_SIZE);
        Map<Integer, WordResult> alignedWords = new TreeMap<>();
        Queue<Range> ranges = new LinkedList<>();
        Queue<List<String>> texts = new ArrayDeque<>();
        Queue<TimeFrame> timeFrames = new ArrayDeque<>();

        ranges.offer(new Range(0, transcript.size()));
        texts.offer(transcript);
        TimeFrame totalTimeFrame = TimeFrame.INFINITE;
        timeFrames.offer(totalTimeFrame);
        long lastFrame = TimeFrame.INFINITE.end;

        languageModel.setText(sentenceTranscript);
        
        for (int i = 0; i < 4; ++i) {
            if (i == 1) {
                context.setLocalProperty("decoder->searchManager", "alignerSearchManager");
            }

            while (!texts.isEmpty()) {
                assert texts.size() == ranges.size();
                assert texts.size() == timeFrames.size();

                List<String> text = texts.poll();
                TimeFrame frame = timeFrames.poll();
                Range range = ranges.poll();


                logger.info("Aligning frame " + frame + " to text " + text + " range " + range);

                recognizer.allocate();

                if (i >= 1) {
                    grammar.setWords(text);
                }

                InputStream stream = audioUrl.openStream();
                try {
                    context.setSpeechSource(stream, frame);

                    List<WordResult> hypothesis = new ArrayList<>();
                    Result result;
                    while (null != (result = recognizer.recognize())) {
                        logger.info("Utterance result " + result.getTimedBestResult(true));
                        hypothesis.addAll(result.getTimedBestResult(false));
                    }

                    if (i == 0) {
                        if (hypothesis.size() > 0) {
                            lastFrame = hypothesis.get(hypothesis.size() - 1).timeFrame.end;
                        }
                    }

                    List<String> words = new ArrayList<>(hypothesis.size());
                    for (WordResult wr : hypothesis) {
                        words.add(wr.word.spelling);
                    }
                    int[] alignment = aligner.align(words, range);

                    logger.info("Decoding result is " + hypothesis);

                    // dumpAlignment(transcript, alignment, results);
                    dumpAlignmentStats(transcript, alignment, hypothesis);

                    for (int j = 0; j < alignment.length; j++) {
                        if (alignment[j] != -1) {
                            alignedWords.put(alignment[j], hypothesis.get(j));
                        }
                    }
                } finally {
                    stream.close();
                    recognizer.deallocate();
                }
            }

            scheduleNextAlignment(transcript, alignedWords, ranges, texts, timeFrames, lastFrame);
        }

        return new ArrayList<>(alignedWords.values());
    }

    public static List<String> sentenceToWords(List<String> sentenceTranscript) {
        ArrayList<String> transcript = new ArrayList<>();
        for (String sentence : sentenceTranscript) {
            String[] words = sentence.split("\\s+");
            for (String word : words) {
        	    if (!word.isEmpty())
    	            transcript.add(word);
            }
        }
        return transcript;
    }

    private void dumpAlignmentStats(List<String> transcript, int[] alignment, List<WordResult> results) {
        int insertions = 0;
        int deletions = 0;
        int size = transcript.size();

        int[] aid = alignment;
        int lastId = -1;
        for (int anAid : aid) {
            if (anAid == -1) {
                insertions++;
            } else {
                if (anAid - lastId > 1) {
                    deletions += anAid - lastId;
                }
                lastId = anAid;
            }
        }

        if (lastId >= 0 && transcript.size() - lastId > 1) {
            deletions += transcript.size() - lastId;
        }
        logger.info(String.format("Size %d deletions %d insertions %d error rate %.2f", size, insertions, deletions,
                (insertions + deletions) / ((float) size) * 100f));
    }

    private void scheduleNextAlignment(List<String> transcript, Map<Integer, WordResult> alignedWords, Queue<Range> ranges,
            Queue<List<String>> texts, Queue<TimeFrame> timeFrames, long lastFrame) {
        int prevKey = 0;
        long prevStart = 0;
        for (Map.Entry<Integer, WordResult> e : alignedWords.entrySet()) {
            if (e.getKey() - prevKey > 1) {
                checkedOffer(transcript, texts, timeFrames, ranges, prevKey, e.getKey() + 1, prevStart, e.getValue().timeFrame.end);
            }
            prevKey = e.getKey();
            prevStart = e.getValue().timeFrame.start;
        }
        if (transcript.size() - prevKey > 1) {
            checkedOffer(transcript, texts, timeFrames, ranges, prevKey, transcript.size(), prevStart, lastFrame);
        }
    }

    public void dumpAlignment(List<String> transcript, int[] alignment, List<WordResult> results) {
        logger.info("Alignment");
        int[] aid = alignment;
        int lastId = -1;
        for (int ij = 0; ij < aid.length; ++ij) {
            if (aid[ij] == -1) {
                logger.info(String.format("+ %s", results.get(ij)));
            } else {
                if (aid[ij] - lastId > 1) {
                    for (String result1 : transcript.subList(lastId + 1, aid[ij])) {
                        logger.info(String.format("- %-25s", result1));
                    }
                } else {
                    logger.info(String.format("  %-25s", transcript.get(aid[ij])));
                }
                lastId = aid[ij];
            }
        }

        if (lastId >= 0 && transcript.size() - lastId > 1) {
            for (String result1 : transcript.subList(lastId + 1, transcript.size())) {
                logger.info(String.format("- %-25s", result1));
            }
        }
    }

    private void checkedOffer(List<String> transcript, Queue<List<String>> texts, Queue<TimeFrame> timeFrames,
            Queue<Range> ranges, int start, int end, long timeStart, long timeEnd) {

        double wordDensity = ((double) (timeEnd - timeStart)) / (end - start);

        // Skip range if it's too short, average word is less than 10
        // milliseconds
        if (wordDensity < 10.0 && (end - start) > 3) {
            logger.info("Skipping text range due to a high density " + transcript.subList(start, end).toString());
            return;
        }

        texts.offer(transcript.subList(start, end));
        timeFrames.offer(TimeFrame.time(timeStart, timeEnd));
        ranges.offer(new Range(start, end - 1));
    }

    public TextTokenizer getTokenizer() {
        return tokenizer;
    }

    public void setTokenizer(TextTokenizer wordExpander) {
        this.tokenizer = wordExpander;
    }
}
