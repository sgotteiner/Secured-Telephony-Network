package sip;

public class ClientDetails {

    private String username;
    private String displayName;
    private String domainName;
    private String myIP;
    private int mySipPort;
    private String serverAddress;

    public ClientDetails(String username, String displayName, String domainName,
                         String myIP, int mySipPort, String serverAddress){
        this.username = username;
        this.displayName = displayName;
        this.domainName = domainName;
        this.myIP = myIP;
        this.mySipPort = mySipPort;
        this.serverAddress = serverAddress;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDomainName() {
        return domainName;
    }

    public String getMyIP() {
        return myIP;
    }

    public boolean hasSameDetails(String myIP, int myPort, String serverAddress) {
        if(this.myIP.equals(myIP) || this.mySipPort == myPort || this.serverAddress.equals(serverAddress))
            return true;
        return false;
    }
}
