package tools;

import java.util.Collection;
import java.util.Iterator;

public class MyList<E extends MyListNode> implements Iterable<E>{
    public MyListNode head;
    public MyListNode tail;
    public int size;

    public MyList() {
        this.head = new MyListNode();
        this.tail = new MyListNode();
        head.setNext(tail);
        tail.setPrev(head);
        size = 0;
    }

    public E getFirst() {
        return (E) head.getNext();
    }

    public E getLast() {
        return (E) tail.getPrev();
    }

    public void insertAfter(E nodeToInsert, E pos) {
        pos.insertAfter(nodeToInsert);
        this.size++;
    }

    public void insertBefore(E nodeToInsert, E pos) {
        pos.insertBefore(nodeToInsert);
        this.size++;
    }

    public void insertAtHead(E nodeToInsert) {
        this.head.insertAfter(nodeToInsert);
        this.size++;
    }

    public void insertAtTail(E nodeToInsert) {
        this.tail.insertBefore(nodeToInsert);
        this.size++;
    }

    public void remove(E nodeToRemove) {
        nodeToRemove.remove();
        size--;
    }

    public void removeAll(Collection<E> nodesToRemove) {
        for (E node : nodesToRemove) {
            remove(node);
        }
    }

    public boolean isEmpty() {
        return this.head.getNext().equals(tail);
    }

    public void concat(MyList<E> list) {
        if(list.head.getNext() != list.tail) {
            list.head.getNext().setPrev(this.getLast());
            list.getLast().setNext(this.tail);
            this.tail.getPrev().setNext(list.getFirst());
            this.tail.setPrev(list.getLast());
            this.size += list.size;
        }
    }

    class MyIterator implements Iterator<E> {
        MyListNode cur = head;

        MyIterator(){
        }

        @Override
        public boolean hasNext() {
            return cur.getNext() != tail;
        }

        @Override
        public E next() {
            cur = cur.getNext();
            return (E) cur;
        }

        public void remove() {
            cur.remove();
            size--;
        }
    }

    public Iterator<E> iterator() {
        return new MyIterator();
    }
}
