package aioChatRoom;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @Auther: zls
 * @Date: 2022/2/9 10:56
 * @Description:
 */
public class ChatServer {

    private static final String LOCALHOST = "localhost";
    private static final int DEFAULT_PORT = 8888;
    private static final String QUIT = "quit";
    private static final int BUFFER = 1024;

    private AsynchronousServerSocketChannel serverChannel;
    private AsynchronousChannelGroup asynchronousChannelGroup;
    private List<ClientHandler> connectedClients;
    private Charset charset = StandardCharsets.UTF_8;
    private int port;

    public ChatServer(int port) {
        this.port = port;
        connectedClients = new ArrayList<>();
    }

    public ChatServer() {
        this(DEFAULT_PORT);
    }

    public void start(){
        try {
            //自定义ChannelGroup
            ExecutorService executorService = Executors.newFixedThreadPool(10);
            asynchronousChannelGroup = AsynchronousChannelGroup.withThreadPool(executorService);

            serverChannel = AsynchronousServerSocketChannel.open(asynchronousChannelGroup);
            serverChannel.bind(new InetSocketAddress(LOCALHOST,port));
            System.out.println("服务器已经启动成功，随时等待客户端连接...");

            while (true){
                serverChannel.accept(null,new AcceptHandler());

                System.in.read();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            close(serverChannel);
        }
    }

    private class AcceptHandler implements CompletionHandler<AsynchronousSocketChannel,Object> {
        @Override
        public void completed(AsynchronousSocketChannel clientChannel, Object attachment) {
            if(serverChannel.isOpen())
                serverChannel.accept(null,this);

            if(clientChannel != null && clientChannel.isOpen()) {
                ClientHandler clientHandler = new ClientHandler(clientChannel);

                ByteBuffer buffer = ByteBuffer.allocate(BUFFER);
                addClient(clientHandler); // 添加用户
                clientChannel.read(buffer, buffer, clientHandler);
            }

        }

        @Override
        public void failed(Throwable exc, Object attachment) {
            System.out.println("连接失败：" + exc.getMessage());
        }
    }

    private class ClientHandler implements CompletionHandler<Integer,ByteBuffer>{

        private AsynchronousSocketChannel clientChannel;

        public ClientHandler(AsynchronousSocketChannel clientChannel) {
            this.clientChannel = clientChannel;
        }

        public AsynchronousSocketChannel getClientChannel() {
            return clientChannel;
        }

        @Override
        public void completed(Integer result, ByteBuffer buffer) {
            if(buffer != null) {
                //buffer不为空的时候，这里执行的是read之后的回调方法
                if(result <= 0){
                    //客户端异常，将客户端从连接列表中移除
                    removeClient(this);
                } else {
                    buffer.flip();
                    String fwdMsg = receive(buffer);
                    System.out.println(getClientName(clientChannel) + fwdMsg);
                    forwardMsg(clientChannel, fwdMsg);
                    buffer.clear();

                    if(readyToQuit(fwdMsg)) {
                        removeClient(this);
                    } else {
                        clientChannel.read(buffer,buffer,this);
                    }
                }
            }
        }

        @Override
        public void failed(Throwable exc, ByteBuffer attachment) {
            System.out.println("读写操作失败：" + exc.getMessage());
        }
    }

    private synchronized void addClient(ClientHandler clientHandler) {
        connectedClients.add(clientHandler);
        System.out.println(getClientName(clientHandler.getClientChannel()) + "已经连接");
    }

    private synchronized void removeClient(ClientHandler clientHandler) {
        AsynchronousSocketChannel clientChannel = clientHandler.getClientChannel();
        connectedClients.remove(clientHandler);
        System.out.println(getClientName(clientChannel) + "已经断开连接");
        close(clientChannel);
    }

    private void close(Closeable closeable){
        try {
            closeable.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean readyToQuit(String msg){
        return QUIT.equals(msg);
    }

    private synchronized String receive(ByteBuffer buffer) {
        return String.valueOf(charset.decode(buffer));
    }

    private synchronized void forwardMsg(AsynchronousSocketChannel clientChannel, String fwdMsg) {
        for (ClientHandler connectedHandler : connectedClients) {
            AsynchronousSocketChannel client = connectedHandler.getClientChannel();
            if(!client.equals(clientChannel)){
                //注意这个try，catch是自己加的
                try {
                    //将消息存入缓存区中
                    ByteBuffer buffer = charset.encode(getClientName(client) + fwdMsg);
                    //写给每个客户端
                    client.write(buffer, null, connectedHandler);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

    }

    private String getClientName(AsynchronousSocketChannel clientChannel) {
        int port = -1;
        try {
            InetSocketAddress remoteAddress = (InetSocketAddress) clientChannel.getRemoteAddress();
            port = remoteAddress.getPort();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "客户端[" + port + "]:";
    }

    public static void main(String[] args) {
        ChatServer chatServer = new ChatServer();
        chatServer.start();
    }

}
