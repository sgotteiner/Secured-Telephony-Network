package gui;

import sip.Client;
import sip.Proxy;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ProxyGUI implements IMessageInGUI {

    private JPanel panel;
    private JButton btnStartStop;
    private boolean isStart = true;
    private JTextField txtStartIPRegister;
    private JTextField txtUserIPCall;
    private JTextField txtEndIPRegister;
    private JTextField txtStartIPCall;
    private JTextField txtEndIPCall;
    private JButton btnAddCall;
    private JButton btnAddRegister;
    private JButton btnDeleteCall;
    private JButton btnDeleteRegister;
    private JTextArea terminal;
    private JScrollPane scrollPane;
    private JButton btnShowRegister;
    private JButton btnShowCall;

    public ProxyGUI(Proxy proxy) {

        scrollPane.setPreferredSize(new Dimension(getPanel().getWidth(), 100));

        btnStartStop.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (isStart) {
                    proxy.init(ProxyGUI.this);
                    btnStartStop.setText("Stop");
                } else {
                    proxy.stop();
                    btnStartStop.setText("Start");
                }
                isStart = !isStart;
            }
        });

        btnAddCall.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                proxy.addCallPermission(txtUserIPCall.getText(), txtStartIPCall.getText(), txtEndIPCall.getText());
            }
        });

        btnAddRegister.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                proxy.addRegisterPermission(txtStartIPRegister.getText(), txtEndIPRegister.getText());
            }
        });

        btnDeleteCall.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                proxy.deleteCallPermission(txtUserIPCall.getText(), txtStartIPCall.getText(), txtEndIPCall.getText());
            }
        });

        btnDeleteRegister.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                proxy.deleteRegisterPermission(txtStartIPRegister.getText(), txtEndIPRegister.getText());
            }
        });

        btnShowRegister.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (isStart)
                    return;
                proxy.showRegister();
            }
        });

        btnShowCall.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (isStart)
                    return;
                proxy.showCall();
            }
        });
    }

    public JPanel getPanel() {
        return panel;
    }

    public static void main(String[] args) {

        JFrame frame = new JFrame("Proxy");
        frame.setContentPane(new ProxyGUI(Proxy.getInstance()).getPanel());
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
    }

    @Override
    public void printMessage(String message) {
        terminal.append(message + "\n");
    }
}
