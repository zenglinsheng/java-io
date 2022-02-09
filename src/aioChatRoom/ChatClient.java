package aioChatRoom;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * @Auther: zls
 * @Date: 2022/2/9 11:03
 * @Description:
 */
public class ChatClient {

    private static final String LOCALHOST = "localhost";
    private static final int DEFAULT_PORT = 8888;
    private final String QUIT = "quit";
    private final int BUFFER = 1024;

    private String host;
    private int port;
    private AsynchronousSocketChannel clientChannel;
    private Charset charset = StandardCharsets.UTF_8;

    public ChatClient() {
        this(LOCALHOST,DEFAULT_PORT);
    }

    public ChatClient(String host,int port){
        this.host = host;
        this.port = port;
    }

    public void start(){
        try {
            clientChannel = AsynchronousSocketChannel.open();
            Future<Void> connect = clientChannel.connect(new InetSocketAddress(host, port));
            connect.get();
            System.out.println("与服务已成功建立连接");
            new Thread(new UserInputHandler(this)).start();

            ByteBuffer buffer = ByteBuffer.allocate(BUFFER);
            while (clientChannel.isOpen()){
                Future<Integer> read = clientChannel.read(buffer);
                int result = read.get();
                if(result <= 0) {
                    //这里是，当我们输入quit时，在服务器端会自动将我们移除
                    //所以这里关闭就好了
                    close(clientChannel);
                } else {
                    buffer.flip();
                    String msg = String.valueOf(charset.decode(buffer));
                    System.out.println(msg);
                    buffer.clear();
                }
            }
        } catch (IOException | InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }finally {
            close(clientChannel);
        }
    }

    public void sendMsg(String msg){
        if(msg.isEmpty()){
            return;
        } else {
            ByteBuffer buffer = charset.encode(msg);
            Future<Integer> write = clientChannel.write(buffer);
            try {
                write.get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }
    }

    private void close(Closeable closeable){
        try {
            closeable.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean readyToQuit(String msg){
        return QUIT.equals(msg);
    }

    public static void main(String[] args) {
        ChatClient chatClient = new ChatClient();
        chatClient.start();
    }

}
