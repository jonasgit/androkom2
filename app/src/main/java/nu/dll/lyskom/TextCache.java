/**! -*- Mode: Java; c-basic-offset: 4 -*-
 *
 * Copyright (c) 1999 by Rasmus Sten <rasmus@sno.pp.se>
 *
 */
package nu.dll.lyskom;

import java.util.Hashtable;
import java.util.List;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.TreeMap;

class TextCache {
    final static boolean DEBUG = Boolean.getBoolean("lattekom.caches.debug");

    int maxAge = 60 * 60 * 1000 * 6 ; // 6 hours
    int maxSize = 1000;

    Hashtable<Integer, Text> hash;
    TreeMap<Integer, Long> ageMap = new TreeMap<Integer, Long>();

    public TextCache() {
        hash = new Hashtable<Integer, Text>();
    }

    public void remove(int textNo) {
        if (Debug.ENABLED) {
            Debug.println("TextCache.remove(" + textNo + ")");
        }
        hash.remove(Integer.valueOf(textNo));
        ageMap.remove(Integer.valueOf(textNo));
    }

    public void add(Text t) {
        if (t.getNo() == -1)
            return; // throw(new TextNumberException("Text has no number"));
        checkLimits(0);
        if (DEBUG)
            Debug.println("TextCache: adding " + t.getNo());
        if (hash.put(Integer.valueOf(t.getNo()), t) != null) {
            if (DEBUG)
                Debug.println("TextCache: " + "replacing text #" + t.getNo()
                        + " in cache");
        }

        ageMap.put(Integer.valueOf(t.getNo()), Long.valueOf(System.currentTimeMillis()));
    }

    public boolean contains(int n) {
        checkLimits(n);
        return hash.contains(Integer.valueOf(n));
    }

    public Text get(int textNo) {
        Text t = hash.get(Integer.valueOf(textNo));
        if (DEBUG)
            Debug.println("TextCache: returning " + t);
        checkLimits(textNo);
        return t;
    }

    public void clear() {
        hash.clear();
        ageMap.clear();
    }

    /**
     * Experimental LRU garb for the TextCache. Probably the least efficient
     * implementation available. :-)
     */
    synchronized void checkLimits(int textNo) {
        Integer iNo = Integer.valueOf(textNo);
        
        // Remove texts that were added to cache more than maxAge ago
        if (textNo > 0 && ageMap.containsKey(iNo)) {
            long age = System.currentTimeMillis()
                    - ageMap.get(iNo).longValue();
            if (age > maxAge) {
                ageMap.remove(iNo);
                hash.remove(iNo);
                if (DEBUG)
                    Debug.println("TextCache: removed " + iNo + " of age "
                            + age);
            }
        }

        // Remove oldest texts from cache if number of texts is more than maxSize
        if (hash.size() > maxSize) {
            List<Long> ages = new ArrayList<Long>(ageMap.values());
            List<Text> texts = new ArrayList<Text>(hash.values());
            List<Long> sortedAges = new LinkedList<Long>();
            List<Text> sortedTexts = new LinkedList<Text>();

            Iterator<Long> ageIterator = ages.iterator();
            Iterator<Text> textIterator = texts.iterator();
            long lastValue = 0;
            int lastIndex = -1;
            while (ageIterator.hasNext()) {
                Long l = ageIterator.next();
                if (lastIndex == -1) {
                    lastIndex = 0;
                    lastValue = l.longValue();
                    sortedAges.add(l);
                    sortedTexts.add(textIterator.next());
                } else {
                    if (lastValue > l.longValue()) {
                        sortedAges.add(lastIndex + 1, l);
                        sortedTexts.add(lastIndex + 1, textIterator.next());
                    } else {
                        try {
                            sortedAges.add(lastIndex, l);
                            sortedTexts.add(lastIndex, textIterator.next());
                        } catch (java.util.NoSuchElementException e) {
                            if (DEBUG) {
                                Debug.println("lattekom TextCache: Trying to recover from Exception:"
                                        + e);
                                return;
                            }
                        }
                    }
                    lastIndex++;
                }
            }

            Iterator<Text> sortedTextsIterator = sortedTexts.iterator();
            int count = 0;
            while (count < (hash.size() - maxSize)
                    && sortedTextsIterator.hasNext()) {
                Text t = sortedTextsIterator.next();
                if (DEBUG) {
                    Debug.println("TextCache: size trim: removed "
                            + t.getNo()
                            + " of age "
                            + (System.currentTimeMillis() - ageMap
                                    .get(Integer.valueOf(t.getNo())).longValue()));
                }
                remove(t.getNo());
                count++;
            }
        }
    }

}
