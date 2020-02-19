package com.lru;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Notes:
 *
 * 1. Threadsafe (at least supposed to be :-) )
 * 2. locking reads (get) & writes (put) even if one of the two is happening (hence not going
 *          with ReadwriteLock)
 * 3. Have a single & multi-threaded test in the main method
 * @param <K>
 * @param <V>
 */
public class LRU<K, V> {

    private final Map<K, Node<K,V>> cache;
    private final int maxCapacity;

    private final Node<K,V> head;
    private final Node<K,V> tail;

    private final ReentrantLock lock = new ReentrantLock();

    public LRU(int maxCapacity) {
        this.maxCapacity = maxCapacity;
        cache = new ConcurrentHashMap<>(maxCapacity, 1);
        head = new Node(null, null, null, null);
        tail = new Node(null, null, null, null);
        head.next = tail;
        tail.prev = head;
    }

    public Optional<V> get(K key){
        try {
            lock.lock();
            if(cache.containsKey(key)) {
                Node<K, V> n = cache.get(key);
                moveToHead(n);
                return Optional.of(n.value);
            }
        } finally {
            lock.unlock();
        }
        return Optional.empty();
    }

    public void put(K key, V value){
        try {
            lock.lock();
            Node prevValue = cache.put(key, insertIntoHead(new Node(key, value, head, null)));
            if(prevValue != null) {
                prevValue.prev.next = prevValue.next;
            }
            cleanUp();
        } finally {
            lock.unlock();
        }
    }

    String display(){
        try {
            lock.lock();
            StringBuilder sb = new StringBuilder("<<<<<<<<<<<<\n");
            sb.append(cache).append("\n");
            Node cur = head;
            sb.append(cur + ", ");
            while (cur != null && cur.next != null) {
                sb.append(cur.next + ", ");
                cur = cur.next;
            }
            sb.append("\n>>>>>>>>>>>>\n");
            return sb.toString();
        } finally {
            lock.unlock();
        }
    }

    private Node<K,V> insertIntoHead(Node<K,V> node) {
        Node curNext = head.next;
        head.next = node;
        node.prev = head;
        node.next = curNext;
        curNext.prev = node;
        return node;
    }

    /**
     * keeping the locks on this private method - as this could arguably made public
     */
    private void cleanUp() {
        try {
            lock.lock();
            while (this.cache.size() > maxCapacity) {
                this.cache.remove(tail.prev.key);
                tail.prev = tail.prev.prev;
                tail.prev.next = tail;
            }
        } finally {
            lock.unlock();
        }
    }

    private void moveToHead(Node node) {
        Node nodesPrev = node.prev;
        Node nodesNext = node.next;
        nodesPrev.next = nodesNext;
        nodesNext.prev = nodesPrev;
        insertIntoHead(node);
    }

    private class Node<K,V> {
        private final K key;
        private final V value;

        private Node prev;
        private Node next;

        Node(K key, V value, Node prevNode, Node nextNode) {
            this.key = key;
            this.value = value;
            this.prev = prevNode;
            this.next = nextNode;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Node<?, ?> node = (Node<?, ?>) o;
            return Objects.equals(key, node.key) &&
                    Objects.equals(value, node.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key, value);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("Node{");
            sb.append("key=").append(key);
            sb.append(", value=").append(value);
            if(prev != null) {
                sb.append(", prev.key=").append(prev.key);
            }
            if(next != null) {
                sb.append(", next.key=").append(next.key);
            }
            sb.append("}");
            return sb.toString();
        }
    }


    public static void main(String[] args) throws Exception {
        for (int a = 0 ; a < 10; a ++) {
            final int thisIter = a;

            //Single threaded test
            System.out.println("---------------<<<<<<<<<<<>>>>>>>>>>>>-----------------");
            System.out.println("--------------<<SINGLE THREADED TEST>>-----------------");
            System.out.println("---------------<<<<<<<<<<<>>>>>>>>>>>>-----------------");
            LRU<Integer, String> lruSingle = new LRU(5);
            for (int i = 0; i < 100; i++) {
                lruSingle.put(i, "test"+i);
                lruSingle.get(thisIter);
            }
            if(!lruSingle.get(thisIter).isPresent()) {
                throw new Exception("You FUCKED up dude!!");
            } else {
                System.out.println("Test Passed (" +thisIter+ ")");
            }

            System.out.println("---------------<<<<<<<<<<<>>>>>>>>>>>>-----------------");
            System.out.println("---------------<<MULTI THREADED TEST>>-----------------");
            System.out.println("---------------<<<<<<<<<<<>>>>>>>>>>>>-----------------");
            //Multi threaded test
            LRU<Integer, String> lru = new LRU(5);

            int numProcessors = Runtime.getRuntime().availableProcessors();

            ExecutorService writerExec = Executors.newFixedThreadPool(numProcessors);
            final CountDownLatch latch = new CountDownLatch(numProcessors);
            List<Callable<Integer>> callables = new ArrayList<>();
            for (int i = 0; i < numProcessors; i++) {
                final int start = i * 5;
                callables.add(() -> {
                    int end = start + 4;
                    for (int j = start; j <= end; j++) {
                        lru.put(j, "test" + j);
                        lru.get(thisIter);
                    }
                    latch.countDown();
                    return start;
                });
            }
            List<Future<Integer>> futures = writerExec.invokeAll(callables);
            latch.await();
            writerExec.shutdown();
            if(!lru.get(thisIter).isPresent()) {
                throw new Exception("You FUCKED up dude in multi-threaded test!!");
            } else {
                System.out.println("Test Passed (" +thisIter+ ")");
            }
        }
    }
}
