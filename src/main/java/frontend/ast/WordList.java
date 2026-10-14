//关键词序列

package frontend.ast;

import java.util.ArrayList;
import java.util.Arrays;

//词队列，FIFO
public class WordList {
    private ArrayList<Word> words;
    private int index;       //头结点下标

    public WordList() {
        this.words = new ArrayList<>();
        this.index = 0;
    }

    public void addWord(Word word) {
        this.words.add(word);
    }
    public boolean hasNext() {
        return this.index < this.words.size();
    }

    public Word getWord() {
        return this.words.get(this.index);
    }

    public Word getWord(int pos) {
        return this.words.get(this.index + pos);
    }

    public ArrayList<Word> getList() {
        return this.words;
    }

    public void checkEOF() {
        if (!hasNext()) {
            throw new RuntimeException("The wordList has been EMPTY!");
        }
    }

    public Word consumeHead() {
        checkEOF();
        return words.get(index++);
    }

    public Word consumeHead(WordType wordType) {
        checkEOF();
        Word word = this.words.get(index);
        if (word.getType().equals(wordType)) {
            index++;
            return word;
        }
        throw new RuntimeException("Expected " + wordType + ", but got " + word + "!");
    }

    public Word consumeHead(WordType... wordTypes) {
        checkEOF();
        Word word = this.words.get(index);
        for (WordType wordType : wordTypes) {
            if (word.getType().equals(wordType)) {
                index++;
                return word;
            }
        }
        throw new RuntimeException("Expected " + Arrays.toString(wordTypes) + ", but got " + word.toString() + "!");
    }

}
