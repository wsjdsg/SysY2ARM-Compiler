//关键词

package frontend.ast;

public class Word {
    private String content;
    private WordType type;

    public Word(String content, WordType type) {
        this.content = content;
        this.type = type;
    }

    public WordType getType() {
        return this.type;
    }

    public String getContent() {
        return this.content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String toString() {
        return "<" + this.type + " : " + this.content + ">";
    }

    public boolean isIntConst() {
        return this.type.ordinal() <= WordType.HEX_INT.ordinal()
                && this.type.ordinal() >= WordType.DEC_INT.ordinal();
    }

    public boolean isFloatConst() {
        return this.type.ordinal() == WordType.HEX_FLOAT.ordinal()
                || this.type.ordinal() == WordType.DEC_FLOAT.ordinal();
    }

}
