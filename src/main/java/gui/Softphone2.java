package gui;

import sip.Client;

import javax.sip.RequestEvent;
import javax.sip.ServerTransaction;
import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class Softphone2 {

    private JPanel panel;
    private JTextField txtCallByName;
    private JButton btnCallHangupAnswer;
    private boolean isInCall = false, isAnswer = false;
    private JTextField txtUsername;
    private JTextField txtDisplayName;
    private JTextField txtDomainName;
    private JTextField txtMyIP;
    private JTextField txtServerIP;
    private JTextField txtMyPort;
    private JTextField txtServerPort;
    private JButton btnRegister;
    private boolean isRegistered = false;
    private JTextArea terminal;

    private IConnectSipToGUI iConnectSipToGUI;
    private RequestEvent requestEvent;
    private ServerTransaction serverTransaction;

    public Softphone2(Client client) {

        txtCallByName.setText("Client1NameDisplay");
        txtUsername.setText("Client2Name");
        txtDisplayName.setText("Client2NameDisplay");
        txtDomainName.setText("port5061");
        txtMyIP.setText("127.0.0.1");
        txtMyPort.setText("5061");
        txtServerIP.setText("127.0.0.1");
        txtServerPort.setText("5080");

        iConnectSipToGUI = new IConnectSipToGUI() {
            @Override
            public void handleCallInvitation(RequestEvent requestEvent, ServerTransaction serverTransaction) {
                btnCallHangupAnswer.setText("Answer");
                isAnswer = true;
                Softphone2.this.requestEvent = requestEvent;
                Softphone2.this.serverTransaction = serverTransaction;
            }

            @Override
            public void handleBye() {
                btnCallHangupAnswer.setText("Call");
                isInCall = false;
            }
        };

        btnRegister.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                isRegistered = true;
                client.register(txtUsername.getText(), txtDisplayName.getText(), txtDomainName.getText(),
                        txtMyIP.getText(), Integer.parseInt(txtMyPort.getText()),
                        txtServerIP.getText() + ":" + txtServerPort.getText(), iConnectSipToGUI);
            }
        });
        btnCallHangupAnswer.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!isRegistered)
                    return;

                isInCall = !isInCall;
                if (isInCall) {
                    btnCallHangupAnswer.setText("Hang up");
                    if (isAnswer) {
                        client.responseToInvite(requestEvent, serverTransaction);
                        isAnswer = false;
                    } else {
                        client.invite(txtCallByName.getText());
                    }
                } else {
                    client.sendBye();
                }
            }
        });
    }

    public JPanel getPanel() {
        return panel;
    }

    public static void main(String[] args) {

        JFrame frame = new JFrame("Softphone 2");
        frame.setContentPane(new Softphone2(new Client()).getPanel());
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
    }
}
