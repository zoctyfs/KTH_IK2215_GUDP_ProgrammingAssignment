import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class GUDPSocket implements GUDPSocketAPI {
    DatagramSocket datagramSocket;

    final LinkedList<GUDPEndPoint> sendlist;
    final LinkedList<GUDPEndPoint> receivelist;
    public BlockingQueue<GUDPPacket> receivingQUEUE = new LinkedBlockingQueue<>();
    LinkedList<GUDPEndPoint> acklist;
    List<TIMER_JUDGE> timerJudgeList = new ArrayList<>();
    LinkedList<GUDPPacket> FINAL_RECEIVE = new LinkedList<>();
    SendThread sending;
    ReceiveThread receiving = new ReceiveThread();
    ReceiveThread_ACK receiving_ACK;
    Thread sender;
    Thread receiver;
    Thread ACKreceiver;
    int count_send = 0;
//    boolean first_receive_BSN = false;
    public boolean receivestart = true;
    public boolean finishstart = false;
    public boolean close_flag = false;
    public FIRST_DROP packet_drop = new FIRST_DROP();
    public boolean BSN_retran = false;
    public boolean isRunning  = true;
    public int reliabletest = 0;
    FIN_FINISH finFinish = null;


    public GUDPSocket(DatagramSocket socket) {
        datagramSocket = socket;
        //initial lists, thread
        sendlist = new LinkedList<>();
        receivelist = new LinkedList<>();

        sending = new SendThread(sendlist);
//        receiving = new ReceiveThread();
        receiving_ACK = new ReceiveThread_ACK(sendlist);

        sender = new Thread(sending);
        receiver = new Thread(receiving);
        ACKreceiver = new Thread(receiving_ACK);
    }

    public void send(DatagramPacket packet) throws IOException {
//        GUDPPacket gudppacket = GUDPPacket.encapsulate(packet);
//        DatagramPacket udppacket = gudppacket.pack();
//        datagramSocket.send(udppacket);

        System.out.println("count send" + count_send++);
        GUDPEndPoint sendEndPoint = getEndPoint(sendlist,packet.getAddress(),packet.getPort());
//        System.out.println("checkpoint2");
        if (sendEndPoint == null){          //not exist --> create new one
            sendEndPoint = new GUDPEndPoint(packet.getAddress(),packet.getPort());
            synchronized (sendlist){
                sendlist.add(sendEndPoint);
                System.out.println("first endpoint create & add");
            }
        }
//        System.out.println("checkpoint3");
        //start sendthread
        if(!sender.isAlive()){
            sender.setName("sender");
            sender.start();
            System.out.println("sender start");
        }
//        System.out.println("checkpoint4");
        //start receivethread to receive ACK
        if(! ACKreceiver.isAlive()){
            ACKreceiver.setName("ACKreceiver");
            ACKreceiver.start();
            System.out.println("ACKreceiver start");
        }
        /////////////////////////////////////////////////
//        if (close_flag){
//            sendEndPoint.setState(GUDPEndPoint.endPointState.CLOSED);
//        }
//        System.out.println("into sendlist1");
        synchronized (sendlist){
//            System.out.println("into sendlist2");
            GUDPPacket gudpPacket;
            switch (sendEndPoint.getState()){
                case INIT:
                    System.out.println("send state INIT");
                    sendEndPoint.setState(GUDPEndPoint.endPointState.BSN);
                case BSN:
                    System.out.println("send state BSN");
                    //create BSN packet , set attribute
                    ByteBuffer buffer = ByteBuffer.allocate(GUDPPacket.HEADER_SIZE);
                    buffer.order(ByteOrder.BIG_ENDIAN);
                    gudpPacket = new GUDPPacket(buffer);
                    gudpPacket.setType(GUDPPacket.TYPE_BSN);
                    gudpPacket.setSocketAddress((InetSocketAddress) packet.getSocketAddress());
                    gudpPacket.setVersion(GUDPPacket.GUDP_VERSION);

                    Random rand = new Random();
                    int BSNseq = rand.nextInt(GUDPPacket.MAX_DATA_LEN);
                    gudpPacket.setSeqno(BSNseq);
                    gudpPacket.setPayloadLength(0);

                    //initial window attributes, put BSN into bufferlist
                    sendEndPoint.setBase(BSNseq);
                    sendEndPoint.setLast(BSNseq);
                    sendEndPoint.setNextseqnum(BSNseq);

                    sendEndPoint.add(gudpPacket);
                    sendEndPoint.setState(GUDPEndPoint.endPointState.READY);
                    System.out.println("BSN with seqnum = " + BSNseq + " into bufferlist");
                    sendlist.notify();      //wake up one
//                    break;
                case READY:
                    System.out.println("send state READY");
                    //ready to send packet, encapsulate and set attribute
                    gudpPacket = GUDPPacket.encapsulate(packet);
                    gudpPacket.setSeqno(sendEndPoint.getLast()+1);

                    sendEndPoint.setLast(sendEndPoint.getLast()+1);
                    sendEndPoint.add(gudpPacket);

                    System.out.println("gudppacket number:" + gudpPacket.getSeqno() + " address:" + gudpPacket.getSocketAddress() + " ready, type:" +gudpPacket.getType());

                    break;
                case MAXRETRIED:
                    throw new IOException("send() ERROR: max retried on send end point" + sendEndPoint.getRemoteEndPoint().getAddress() + ":" + sendEndPoint.getRemoteEndPoint().getPort());
                case FINISHED:
                    System.err.println("FINISHED cannot send more packets");
                    break;
                case CLOSED:
                    System.err.println("CLOSED cannot send more packets");
                    break;

            }

        }
    }

    public void receive(DatagramPacket packet) throws IOException {
//        byte[] buf = new byte[GUDPPacket.MAX_DATAGRAM_LEN];
//        DatagramPacket udppacket = new DatagramPacket(buf, buf.length);
//        datagramSocket.receive(udppacket);
//        GUDPPacket gudppacket = GUDPPacket.unpack(udppacket);
//        gudppacket.decapsulate(packet);
        if (receivestart){
            receiver.setName("RECEIVER");
            receiver.start();
            System.out.println("receiver start");
            receivestart = false;
        }
        GUDPPacket gudpPacket;
//        synchronized (receivingQUEUE){
            try {
        //            gudpPacket = receiving.receivingQUEUE.take();
                gudpPacket = receivingQUEUE.take();
                System.out.println("receivingQUEUE.take()");

//                if(gudpPacket != null){

//                }

//                try{
//                        Thread.sleep(50);
//                    } catch (InterruptedException e){
//                        System.err.println("InterruptedException in finish()");
//                        e.printStackTrace();
//                    }

            } catch (InterruptedException e){
                throw new RuntimeException(e);
            }
        gudpPacket.decapsulate(packet);
        FINAL_RECEIVE.add(gudpPacket);
        System.out.println("FINAL_RECEIVE:"+FINAL_RECEIVE);
//        }




        ////////////////////////////////////////////////////////////////
//        if(!receiver.isAlive()){
//            receiver.setName("RECEIVER");
//            receiver.start();
//            System.out.println("receiver start");
//        }
//        boolean redo = false;
//        synchronized (receivelist){
//            do {
//                redo = false;
//                // wait for new receive end point, which is created when BSN arrives
//                while (receivelist.size()==0){
//                    try{
//                        receivelist.wait();
//                    }catch (InterruptedException e){
//                        System.err.println("InterruptedException in receive() null receiveEndPoint");
//                        e.printStackTrace();
//                    }
//                }
//                GUDPEndPoint receiveEndPoint = receivelist.getFirst();
//                //wait DATA packet arrive
//                while(receiveEndPoint.isEmptyBuffer()){
//                    try{
//                        receivelist.wait();
//                    }catch (InterruptedException e){
//                        System.err.println("InterruptedException in receive() is empty buffer");
//                        e.printStackTrace();
//                    }
//                }
//                //when receive FIN, remove endpoint + copy packet to application
//                GUDPPacket receivepacket = receiveEndPoint.remove();
//                if(receiveEndPoint.isEmptyBuffer() &&
//                        (receiveEndPoint.getState() == GUDPEndPoint.endPointState.FINISHED)){
//                    receivelist.remove(receiveEndPoint);
//                    redo = true;
//                    //slow down the main thread after receiving all DATA
//                    try{
//                        Thread.sleep(500);
//                    } catch (InterruptedException e){
//                        System.err.println("InterruptedException in finish()");
//                        e.printStackTrace();
//                    }
//                } else {
////                    if (packet!= null){
//                        receivepacket.decapsulate(packet);
//                        System.out.println("copy packet received("+ receivepacket.getSeqno() + ") to application layer");
//                        System.out.println("payload:" + packet.getData());
////                    }
//
//                }
//            } while (redo);
//
//        }
    }

    public void finish() throws IOException {
        System.out.println("finish start");
        finishstart = true;
        //todo 不知道需不需要改到下面去

        while (sendlist.size() > 0){
            int i = 0;
            while (i < sendlist.size()){
                if(sendlist.get(i).getState() == GUDPEndPoint.endPointState.MAXRETRIED){
                    throw new IOException("FINISH() error: max retried on send end point" + sendlist.get(i).getRemoteEndPoint().getAddress() + ":" + sendlist.get(i).getRemoteEndPoint().getPort());
                }
                if((sendlist.get(i).getBase() >= sendlist.get(i).getLast()) && (sendlist.get(i).getState() != GUDPEndPoint.endPointState.FINISHED)){
                    //send finish, remove send endpoint
                    System.out.println("finished send end point:" + sendlist.get(i).getRemoteEndPoint().getAddress() + ":" + sendlist.get(i).getRemoteEndPoint().getPort());
                    sendlist.get(i).setState(GUDPEndPoint.endPointState.FINISHED);
                    //create FIN packets
                    GUDPEndPoint gudpEndPoint;
                    for (int j = 0;j < sendlist.size(); j++){
                        gudpEndPoint = sendlist.get(j);
                        ByteBuffer buffer = ByteBuffer.allocate(GUDPPacket.HEADER_SIZE);
                        buffer.order(ByteOrder.BIG_ENDIAN);
                        GUDPPacket gudpPacket = new GUDPPacket(buffer);
                        gudpPacket.setType(GUDPPacket.TYPE_FIN);
                        gudpPacket.setSocketAddress(gudpEndPoint.getRemoteEndPoint());
                        gudpPacket.setVersion(GUDPPacket.GUDP_VERSION);
                        gudpPacket.setPayloadLength(0);
                        int FIN_seq = gudpEndPoint.getNextseqnum();
                        gudpPacket.setSeqno(FIN_seq);
//                        gudpEndPoint.setLast(FIN_seq+1);            //!!!!!!!!+1！！！！！！！！！！！
                        gudpEndPoint.setLast(gudpEndPoint.getLast()+1);
                        gudpEndPoint.add(gudpPacket);
                        System.out.println("FIN packets created and added");
                    }
                    continue;
                } else {
                    i++;
                }
            }
            //slow down the main thread
            try {
                Thread.sleep(50);
            } catch (InterruptedException e){
                System.err.println("InterruptedException in finish()");
                e.printStackTrace();
            }

            System.out.println("do not leave finish() successfully");
            if (finFinish!=null && finFinish.FIN_ACK_received){
                sendlist.remove();
                finFinish.FIN_ACK_received = false;
            }
        }
    }
    public void close() throws IOException {
        System.out.println("close begin");
//        System.exit(0);
        close_flag = true;
        synchronized (sending){
            sending.stop_sending();
            sending.notifyAll();
        }
        synchronized (receiving){
            receiving.stop_receiving();
            receiving.notifyAll();
        }
        synchronized (receiving_ACK){
            receiving_ACK.stop_receiving_ACK();
            receiving_ACK.notifyAll();
        }
        datagramSocket.close();
        System.out.println("all socket close");
    }

    public class SendThread implements Runnable{
        private final LinkedList<GUDPEndPoint> sendingList;
        private boolean runFlag = true;
        public SendThread(LinkedList<GUDPEndPoint> sendingList){
            this.sendingList = sendingList;
        }



        public void stop_sending(){
            runFlag = false;
        }
        @Override
        public void run() {
            while(this.runFlag){
                synchronized (sendingList){
                    System.out.println("sendthread run");
                    if (sendingList.size() == 0){
                        try{
                            sendingList.wait();
                        } catch (InterruptedException e){
                            System.err.println("sendthread interrupted because of socket closed");
                            e.printStackTrace();
                        }
                    }

                    //iterate all endpoint
                    int i = 0;
                    while ((i < sendingList.size()) && (sendingList.size() > 0) && (this.runFlag)){
                        GUDPEndPoint gudpEndPoint = sendingList.get(i);
                        System.out.println("sendlist endpoint state = " + gudpEndPoint.getState());
                        switch (gudpEndPoint.getState()){
                            case INIT:
                                System.out.println("sendthread INIT");
                                break;
                            case BSN:
                                System.out.println("sendthread BSN");
                                break;
                            case MAXRETRIED:
                                System.err.println("retry:" + gudpEndPoint.getRetry());
                                gudpEndPoint.setState(GUDPEndPoint.endPointState.FINISHED);
                                break;
                            case FINISHED:
                                System.out.println("sendthread FINISHED");
                                if (gudpEndPoint.isEmptyBuffer()){
                                    System.out.println("remove send endpoint " + gudpEndPoint.getRemoteEndPoint());
                                    sendingList.remove(gudpEndPoint);
                                    continue;
                                }
                            case READY:
                                System.out.println("sendthread READY goto process send");
                                processSend(gudpEndPoint);
                                break;
                            case CLOSED:
                                System.out.println("sendthread CLOSED");
                                break;
                        }
                        i++;
                    }
                }
                try{
                    Thread.sleep(10);
                } catch (InterruptedException e){
                    throw new RuntimeException(e);
                }

            }
        }
    }

    public class ReceiveThread extends Thread{
        private List<Queue<GUDPPacket>> Receivebuffer = new LinkedList<>();
        protected int receivenumber = -1;
        protected int expectedseqnum = 0;
        boolean runFlag = true;
        public void stop_receiving(){
            runFlag = false;
        }

        @Override
        public void run() {

            while (isRunning) {
//                if (first_receive_BSN == false){
//                    System.out.println("");
//                }
                GUDPPacket receivedPacket = receiveGUDPPacket();
                System.out.println("Receive udppacket " + receivedPacket.getSeqno() + " " + receivedPacket.getSocketAddress() + " TYPE:" + receivedPacket.getType());
//                if (receivedPacket != null) {
                    ensureCurrentQueueExists();
                    Queue<GUDPPacket> currentQueue = Receivebuffer.get(receivenumber);

                    switch (receivedPacket.getType()) {
                        case GUDPPacket.TYPE_BSN:
                            expectedseqnum = receivedPacket.getSeqno() + 1;
                            currentQueue.add(receivedPacket);
                            sendACK(receivedPacket);
                            System.out.println("send BSN ack packet " + receivedPacket.getSeqno() + "  address " + receivedPacket.getSocketAddress());
                            break;
                        case GUDPPacket.TYPE_DATA:
                            if (receivedPacket.getSeqno() == expectedseqnum) {
                                currentQueue.add(receivedPacket);
                                sendACK(receivedPacket);
                                System.out.println("send DATA ack packet " + receivedPacket.getSeqno() + "  address " + receivedPacket.getSocketAddress());
                            }
                            break;
                        case GUDPPacket.TYPE_FIN:
                            sendACK(receivedPacket);
                            System.out.println("FIN packet received, send finish");
                            break;
                        default:
                            // Handle other packet types or ignore
                            // 放弃ACK
                            break;
                    }
                    System.out.println("receivenumber:"+receivenumber+"  Receivebuffer.size():"+Receivebuffer.size());
                    if (receivenumber < 0 || receivenumber >= Receivebuffer.size()) {
                        return;
                    }

//                    Queue<GUDPPacket> currentQueue = gudpBufferQueueList.get(currentIndex);
//                    Queue<GUDPPacket> currentQueue = Receivebuffer.get(receivenumber);
                    synchronized (receivingQUEUE){
                        while (true) {
//                        System.out.println("ReceiveBuffer beforepeek:" + currentQueue);
                            GUDPPacket addPacket = currentQueue.peek();
                            System.out.println("ReceiveBuffer:" + Receivebuffer);
                            System.out.println("currentQueue:" + currentQueue);
                            System.out.println("addPacket:" + addPacket);
                            if (addPacket == null){
                                System.out.println("packet is null");
                                break;
                            }
                            if (addPacket.getSeqno() < expectedseqnum) {
                                currentQueue.remove();
                                System.out.println("currentQueue.remove()");
                                continue;
                            }
                            receivingQUEUE.add(addPacket);
                            System.out.println("receivingQUEUE:" + receivingQUEUE);
                            currentQueue.remove();
                            expectedseqnum++;
                            System.out.println("expectedseqnum:" + expectedseqnum);
                        }
                    }

//                }
            }
        }
        private void sendACK(GUDPPacket gudpPacket){
            InetSocketAddress inetSocketAddress = gudpPacket.getSocketAddress();
            int seq = gudpPacket.getSeqno();
            DatagramPacket udppacket;
            GUDPPacket ack;
            ByteBuffer buffer = ByteBuffer.allocate(GUDPPacket.HEADER_SIZE);
            buffer.order(ByteOrder.BIG_ENDIAN);
            ack = new GUDPPacket(buffer);
            ack.setType(GUDPPacket.TYPE_ACK);
            ack.setSeqno(seq);
            ack.setVersion(GUDPPacket.GUDP_VERSION);
            ack.setPayloadLength(0);
            ack.setSocketAddress(inetSocketAddress);
            //reliability test
//            packet_drop.inetSocketAddress = gudpPacket.getSocketAddress();
//            packet_drop.packet = gudpPacket.getType();
            //
            try {
                udppacket = ack.pack();
//                if (!packet_drop.isdrop_first){
                datagramSocket.send(udppacket);
//                }
                System.out.println("packet ACK:"+seq+" address:"+inetSocketAddress+" drop?:"+packet_drop.isdrop_first);
            } catch (IOException e){
                System.err.println("ERROR in sending ack");
                e.printStackTrace();
            }
            packet_drop.isdrop_first = false;
        }
        private GUDPPacket receiveGUDPPacket() {
            try {
                byte[] buffer = new byte[GUDPPacket.MAX_DATAGRAM_LEN];
                DatagramPacket udpPacket = new DatagramPacket(buffer, buffer.length);
                datagramSocket.receive(udpPacket);

                return GUDPPacket.unpack(udpPacket);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        private void ensureCurrentQueueExists() {
            if (receivenumber < 0 || receivenumber >= Receivebuffer.size()) {
                Receivebuffer.add(new PriorityQueue<>(Comparator.comparingInt(GUDPPacket::getSeqno)));
                receivenumber = Receivebuffer.size() - 1;
                System.out.println("receive number reset as:"+receivenumber);
            }
        }


        ////////////////////////////////////////////////////////////////////////
//        private final LinkedList<GUDPEndPoint> receivingList;
//        public BlockingQueue<GUDPPacket> receivingQUEUE;
//        private boolean runFlag = true;
//        public ReceiveThread(LinkedList<GUDPEndPoint> receivingList){
//
//            this.receivingList = receivingList;
//            this.ReceiveBuffer = new PriorityQueue<>(Comparator.comparingInt(GUDPPacket::getSeqno));
//            this.receivingQUEUE = new LinkedBlockingQueue<>();
//        }
//        public void stop_receiving(){
//            runFlag = false;
//        }
//        private Queue<GUDPPacket> ReceiveBuffer;
//        public int receive_number = -1;
//        public int expectseq = 0;
//
////        private void sendACK(GUDPEndPoint endPoint,GUDPPacket gudpPacket){
//        private void sendACK(GUDPPacket gudpPacket){
//            InetSocketAddress inetSocketAddress = gudpPacket.getSocketAddress();
//            int seq = gudpPacket.getSeqno();
//            DatagramPacket udppacket;
//            GUDPPacket ack;
//            ByteBuffer buffer = ByteBuffer.allocate(GUDPPacket.HEADER_SIZE);
//            buffer.order(ByteOrder.BIG_ENDIAN);
//            ack = new GUDPPacket(buffer);
//            ack.setType(GUDPPacket.TYPE_ACK);
//            ack.setSeqno(seq);
//            ack.setVersion(GUDPPacket.GUDP_VERSION);
//            ack.setPayloadLength(0);
//            ack.setSocketAddress(inetSocketAddress);
//            //reliability test
//            packet_drop.inetSocketAddress = gudpPacket.getSocketAddress();
//            packet_drop.packet = gudpPacket.getType();
//            //
//            try {
//                udppacket = ack.pack();
////                if (!packet_drop.isdrop_first){
//                    datagramSocket.send(udppacket);
////                }
//                System.out.println("packet ACK:"+seq+" address:"+inetSocketAddress+" drop?:"+packet_drop.isdrop_first);
//            } catch (IOException e){
//                System.err.println("ERROR in sending ack");
//                e.printStackTrace();
//            }
//            packet_drop.isdrop_first = false;
//        }
//
//        @Override
//        public void run() {
//            while (this.runFlag) {
//
//                try {
//                    GUDPPacket gudpPacket;
//                    byte[] buf = new byte[GUDPPacket.MAX_DATAGRAM_LEN];
//                    DatagramPacket udppacket = new DatagramPacket(buf, buf.length);
//                    datagramSocket.receive(udppacket);
//                    gudpPacket = GUDPPacket.unpack(udppacket);
//                    System.out.println("Receive udppacket " + gudpPacket.getSeqno() + " " + gudpPacket.getSocketAddress() + " TYPE:" + gudpPacket.getType());
//
//
//                if (receive_number < 0){
//
//                }
//                    switch (gudpPacket.getType()) {
//                        case GUDPPacket.TYPE_BSN:
//                            ReceiveBuffer.add(gudpPacket);
//                            expectseq = gudpPacket.getSeqno() + 1;
//                            sendACK(gudpPacket);
//                            System.out.println("send BSN ack packet " + gudpPacket.getSeqno() + "address " + gudpPacket.getSocketAddress());
//                            break;
//                        case GUDPPacket.TYPE_DATA:
//                            if (gudpPacket.getSeqno() == expectseq) {
//                                ReceiveBuffer.add(gudpPacket);
//                                sendACK(gudpPacket);
//                                System.out.println("send DATA ack packet " + gudpPacket.getSeqno() + "address " + gudpPacket.getSocketAddress());
//                            }
//                            break;
//                        case GUDPPacket.TYPE_FIN:
//                            sendACK(gudpPacket);
//                            System.out.println("FIN packet received, send finish");
//                            break;
//                    }
//                    while (true) {
//                        GUDPPacket gudpPacket1 = ReceiveBuffer.peek();
//    //                    if(gudpPacket1 != null){
//                        if (Objects.isNull(gudpPacket1)) {
//                            break;
//                        }
//                        if (gudpPacket1.getSeqno() >= expectseq) {
//                            expectseq++;
//                            ReceiveBuffer.remove();
//                            receivingQUEUE.add(gudpPacket);
//                            System.out.println("receivingQUEUE:" + receivingQUEUE);
//                            System.out.println("ReceiveBuffer:" + ReceiveBuffer);
//                        } else {
//                            ReceiveBuffer.remove();
//                        }
//                    }
//                }catch (IOException e) {
//                    throw new RuntimeException(e);
//                }
//                //                if (receive_number == 0 || receive_number >= ReceiveBuffer.size()){
//                ////                    ReceiveBuffer.add();
//                //                    receive_number = ReceiveBuffer.size() -1;
//                //                }
//            }
//        }
//        ///////////////////////////////////////////////////
//                synchronized (receivingList){
//                    GUDPPacket gudpPacket;
//                    GUDPEndPoint endPoint;
//                    try{
//                        datagramSocket.receive(udppacket);
//                        gudpPacket = GUDPPacket.unpack(udppacket);
//                        System.out.println("Receive udppacket " + gudpPacket.getSeqno() + " " + gudpPacket.getSocketAddress()+" TYPE:"+gudpPacket.getType());
//                    } catch (IOException e){
//                        throw new RuntimeException(e);
//                    }
//                    InetSocketAddress inetSocketAddress = gudpPacket.getSocketAddress();
//                    int rec_seq = gudpPacket.getSeqno();
//                    switch (gudpPacket.getType()){
//                        case GUDPPacket.TYPE_BSN:
//                            //first time receive-->create
//                            endPoint = getEndPoint(receivingList, inetSocketAddress.getAddress(),inetSocketAddress.getPort());
//                            if (endPoint == null){
//                                endPoint = new GUDPEndPoint(inetSocketAddress.getAddress(),inetSocketAddress.getPort());
//                                endPoint.setExpectedseqnum(rec_seq+1);
//                                receivingList.add(endPoint);
//                            } else {
//                                endPoint.removeAll();
//                                System.out.println("endpoint remove all packets inside");
//                            }
//                            receivingList.notifyAll();
//                            sendACK(endPoint,gudpPacket);
//                            System.out.println("send BSN ack packet "+ rec_seq+ "address "+endPoint.getRemoteEndPoint());
//                            break;
//
//                        case GUDPPacket.TYPE_DATA:
//                            synchronized (receivingList){
//                                endPoint = getEndPoint(receivingList, inetSocketAddress.getAddress(),inetSocketAddress.getPort());
//                                if (endPoint == null){
//                                    System.err.println("WARN: DATA arrives before receiving a BSN");
//                                    BSN_retran = true;
//                                    break;
//                                }
//                                if((rec_seq==endPoint.getExpectedseqnum())){
//                                    endPoint.add(gudpPacket);
//                                    endPoint.setExpectedseqnum(rec_seq+1);
//                                    receivingList.notifyAll();
//                                    sendACK(endPoint, gudpPacket);
//                                    System.out.println("send DATA ack packet "+ rec_seq+ "address "+endPoint.getRemoteEndPoint());
//                                }
//                            }
//
//                            break;
//
//                        case GUDPPacket.TYPE_ACK:
//                            break;
//
//                        case GUDPPacket.TYPE_FIN:
//                            endPoint = getEndPoint(receivingList, inetSocketAddress.getAddress(), inetSocketAddress.getPort());
//
//                            if (endPoint == null){
//                                System.err.println("WARN: FIN arrives before receiving a BSN");
//                                break;
//                            }
//                            endPoint.setState(GUDPEndPoint.endPointState.FINISHED);
////                            receivingList.notifyAll();
//                            sendACK(endPoint,gudpPacket);
//                            System.out.println("FIN packet received, send finish");
////                            ////////////////////////////////////////
//                            try{
//                                close();
//                            }catch (IOException e){
//                                throw new RuntimeException(e);
//                            }
//
//                            break;
//
//                    }
////                }
//                try {
//                    Thread.sleep(2);
//                } catch (InterruptedException e){
//                    throw new RuntimeException(e);
//                }
//            }

    }

    public class ReceiveThread_ACK implements Runnable{
        public ReceiveThread_ACK(LinkedList<GUDPEndPoint> receivingList_ACK){
            this.receivingList_ACK = receivingList_ACK;
        }
        private final LinkedList<GUDPEndPoint> receivingList_ACK;
        private boolean runFlag = true;

        public void stop_receiving_ACK(){
            runFlag = false;
        }

        @Override
        public void run() {
            byte[] buf = new byte[GUDPPacket.MAX_DATAGRAM_LEN];
            DatagramPacket udppacket = new DatagramPacket(buf,buf.length);
            System.out.println("receive ACK begin");
            while (this.runFlag){
                GUDPPacket gudpPacket;
                GUDPEndPoint endPoint;
                try{
                    datagramSocket.receive(udppacket);
                    gudpPacket = GUDPPacket.unpack(udppacket);
                    System.out.println("Receive ackudppacket "+ gudpPacket.getSeqno()+" "+gudpPacket.getSocketAddress());
                } catch (IOException e){
                    throw new RuntimeException(e);
                }
                InetSocketAddress inetSocketAddress = gudpPacket.getSocketAddress();
                int rec_seq = gudpPacket.getSeqno();
                if(gudpPacket.getType()==GUDPPacket.TYPE_ACK){
                    endPoint = getEndPoint(receivingList_ACK,inetSocketAddress.getAddress(),inetSocketAddress.getPort());
                    if (endPoint == null){
                        System.err.println("WARN: can't find the correct socketaddress for this ACK");
                        break;
                    } else {    //find the correct one, goto sendlist
                        synchronized (receivingList_ACK){
                            endPoint.removeAllACK(rec_seq-1);
                            System.out.println("remove ack before " + rec_seq);
                            endPoint.setBase(rec_seq);
                            System.out.println("receive ACK:" + rec_seq);
                            endPoint.setEvent(GUDPEndPoint.readyEvent.RECEIVE);
                            if (finFinish != null && gudpPacket.getSeqno() == finFinish.FIN_SEQ){
                                System.out.println("receive FIN ACK");
                                finFinish.FIN_ACK_received = true;
                            }
                            processSend(endPoint);
                            receivingList_ACK.notifyAll();
                        }
                    }
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e){
                    throw new RuntimeException(e);
                }
            }
        }
    }


    public GUDPEndPoint getEndPoint(LinkedList<GUDPEndPoint> list, InetAddress addr, int port){
        synchronized (list){
            for (int i = 0; i < list.size(); i++){
                if ((list.get(i).getRemoteEndPoint().getAddress().equals(addr)) && (list.get(i).getRemoteEndPoint().getPort()==port)){
                    return list.get(i);
                }
            }
            return null;
        }
    }


    public void processSend(GUDPEndPoint endPoint){         //acting as "send function"
        synchronized (endPoint){
            DatagramPacket udppacket;
            switch (endPoint.getEvent()){
                case WAIT:
                    break;
                case INIT:
//                    break;
                case SEND:
                    System.out.println("processSend state SEND");
                    synchronized (sendlist){
                        while((endPoint.getNextseqnum() - endPoint.getBase() <= endPoint.getWindowSize()) && (endPoint.getNextseqnum() <= endPoint.getLast())){
                            //第一个到底是小于等于还是小于，虽然感觉都可以
                            if (endPoint.getPacket(endPoint.getNextseqnum()).getType() == GUDPPacket.TYPE_FIN){
                                finFinish = new FIN_FINISH(endPoint,endPoint.getNextseqnum());
                            }
                            try{
//                                if(endPoint.getPacket(endPoint.getNextseqnum()) != null){
                                    udppacket = endPoint.getPacket(endPoint.getNextseqnum()).pack();
//                                    packet_drop.inetSocketAddress = endPoint.getRemoteEndPoint();
//                                    packet_drop.packet = endPoint.getPacket(endPoint.getNextseqnum()).getType();
//                                    FIRST_DROP drop_packet = new FIRST_DROP(endPoint,endPoint.getPacket(endPoint.getNextseqnum()));
//                                    if (!packet_drop.isdrop_first && packet_drop.packet != GUDPPacket.TYPE_BSN) {
                                        System.out.println("actual sending, endpoint Nextseqnum:"+endPoint.getNextseqnum()+" sendlist.size():"+sendlist.size()+" packet_type:"
                                                +endPoint.getPacket(endPoint.getNextseqnum()).getType()+" drop?"+packet_drop.isdrop_first);
                                        datagramSocket.send(udppacket);
//                                    }
                                    System.out.println("udppacket sent, seq " + endPoint.getNextseqnum() + " address " + endPoint.getRemoteEndPoint()+" packet_type:"
                                            +endPoint.getPacket(endPoint.getNextseqnum()).getType());
//                                    packet_drop.isdrop_first = false;
//                                    if (BSN_retran){    //开始暴力重传BSN
//                                        DatagramPacket udppacket_bsn;
//                                    }
//                                }
                            } catch (IOException e){
                                System.err.println("IOException IN SENDprocess: SEND");
                                e.printStackTrace();
                            }
                            System.out.println("endPoint base:"+endPoint.getBase()+" endPoint Nextseqnum:"+endPoint.getNextseqnum()+" windowsize:"+endPoint.getWindowSize());
//                            if (endPoint.getBase() == endPoint.getNextseqnum()){
                            if (endPoint.getNextseqnum() - endPoint.getBase() == endPoint.getWindowSize()) {
                                endPoint.startTimer();
                                System.out.println("timer start, sending window finish");
                                sendlist.notifyAll();
                            }
//                            if (reliabletest<=3){
                                endPoint.setNextseqnum(endPoint.getNextseqnum()+1);                 //reliability test
//                                reliabletest++;
//                            }else {
//                                endPoint.setNextseqnum(endPoint.getNextseqnum()-2);
//                                reliabletest = 0;
//                            }
//                            try {
//                                Thread.sleep(50);
//                            } catch (InterruptedException e){
//                                throw new RuntimeException(e);
//                            }
                        }
                    }

                    if ((endPoint.getBase() > endPoint.getLast()) && (endPoint.getState()== GUDPEndPoint.endPointState.FINISHED)){
                        endPoint.stopTimer();
                    }
                    break;

                case TIMEOUT:
                    System.out.println("processSend state TIMEOUT");
                    if (endPoint.getRetry() >= endPoint.getMaxRetry()){
                        //Is it necessary to increase the number of retransmissions, there will be inexplicable timeout phenomenon Modifying the number of times is useless, or to change the small sleep value,
                        // can not find out the problem. Maybe there is a problem with the time
                        endPoint.setState((GUDPEndPoint.endPointState.MAXRETRIED));
                        System.err.println("max retransmission timeout:" + endPoint.getMaxRetry());
                        System.err.println("terminate sending");
                        try{
                            close();
                        } catch (IOException e){
                            System.err.println("IOException error in processSend: TIMEOUT");
                            e.printStackTrace();
                        }
//                        System.exit(1);
                        break;
                    }
                    //ARQ set range
                    endPoint.startTimer();
                    System.out.println("TIMEOUT timer start");
                    int resend = endPoint.getBase();
                    int end;
                    if (endPoint.getLast() - resend < endPoint.getWindowSize()){
                        end = endPoint.getLast();
                    }
                    else {
                        end = resend + endPoint.getWindowSize();        /////////remove -1
                    }
                    while (resend <= end){
                        try {
                            udppacket = endPoint.getPacket(resend).pack();
                            datagramSocket.send(udppacket);
                            System.out.println("resend packets:" + resend + "type:" + endPoint.getPacket(resend).getType());
                            if (resend > endPoint.getNextseqnum()){
                                endPoint.setNextseqnum(resend);
                            }
                        } catch (IOException e){
                            System.err.println("IOException in TIMEOUT");
                            e.printStackTrace();
                        }
                        resend++;
                    }
                    endPoint.setRetry(endPoint.getRetry()+1);
                    endPoint.setEvent(GUDPEndPoint.readyEvent.SEND);
                    break;
                case RECEIVE:
                    System.out.println("processSend state RECEIVE");
                    TIMER_JUDGE isTimerStarted = new TIMER_JUDGE(endPoint.getRemoteEndPoint());
                    if (endPoint.getBase() == endPoint.getNextseqnum()){
//                    if (endPoint.getNextseqnum()-endPoint.getBase() == endPoint.getWindowSize()){
//                        if (isTimerStarted.timer_judge) {  // Check if timer has been started
                            endPoint.stopTimer();
                            System.out.println("RECEIVE timer stop");
//                            isTimerStarted.timer_judge = false;  // Update the flag
//                        }
                        endPoint.setRetry(0);
                        System.out.println("ack received successfully");
                    } else {            //reset timer
                        if (isTimerStarted.timer_judge) {  // Check if timer has been started
                            endPoint.stopTimer();
                            isTimerStarted.timer_judge = false;  // Update the flag
                        }           //avoid "time is null"
                        endPoint.startTimer();
                        isTimerStarted.timer_judge = true;
                        System.out.println("RECEIVE reset timer");
//                        endPoint.setBase(endPoint.getBase()+1);
                    }
                    endPoint.setEvent(GUDPEndPoint.readyEvent.SEND);
                    break;
            }
        }
    }
    public class TIMER_JUDGE {
        public InetSocketAddress inetSocketAddress;
        public boolean timer_judge;
        public TIMER_JUDGE (InetSocketAddress ADDRS){
            inetSocketAddress = ADDRS;
            timer_judge = false;
        }

    }
    public class FIRST_DROP {
        public InetSocketAddress inetSocketAddress;
        public boolean isdrop_random;   //false-->sent,true-->drop
        public boolean isdrop_first;
        public short packet;
        public FIRST_DROP (){
//            inetSocketAddress = endPoint.getRemoteEndPoint();
//            packet = gudppacket.getType();
            Random random = new Random();
            isdrop_random = random.nextBoolean();
            isdrop_first = true;
        }

    }

    public class FIN_FINISH{

        public GUDPEndPoint gudpEndPoint;
        public int FIN_SEQ;
        public boolean FIN_ACK_received = false;
        public FIN_FINISH(GUDPEndPoint endPoint, int seq){
            gudpEndPoint = endPoint;
            FIN_SEQ = seq;
        }

    }
}

