package aioChatRoom;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * @Auther: zls
 * @Date: 2022/2/9 11:03
 * @Description:
 */
public class UserInputHandler implements Runnable {

    private ChatClient client;

    public UserInputHandler(ChatClient client) {
        this.client = client;
    }

    @Override
    public void run() {
        BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));

        while (true) {
            try {
                String msg = consoleReader.readLine();
                client.sendMsg(msg);

                if(client.readyToQuit(msg)){
                    System.out.println("成功退出聊天室");
                    break;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
