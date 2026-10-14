package tools;

public class MyListNode {
    private MyListNode prev = null;
    private MyListNode next = null;

    public MyListNode getPrev() {
        return prev;
    }

    public void setPrev(MyListNode prev) {
        this.prev = prev;
    }

    public MyListNode getNext() {
        return next;
    }

    public void setNext(MyListNode next) {
        this.next = next;
    }

    public boolean hasPrev() {
        return this.prev != null;
    }

    public boolean hasNext() {
        return this.next != null;
    }

    public void insertAfter(MyListNode node) {        //A.insertAfter(B)  将B插入A后
        node.setPrev(this);
        node.setNext(this.getNext());
        if(this.hasNext()) {
            this.getNext().setPrev(node);
        }
        this.setNext(node);
    }

    public void insertBefore(MyListNode node) {
        node.setNext(this);
        node.setPrev(this.getPrev());
        if(this.hasPrev()) {
            this.getPrev().setNext(node);
        }
        this.setPrev(node);
    }

    public void remove() {
        if(this.hasPrev()) {
            this.getPrev().setNext(this.getNext());
        }

        if(this.hasNext()) {
            this.getNext().setPrev(this.getPrev());
        }
    }
}
