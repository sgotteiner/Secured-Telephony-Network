package db;

import gui.IMessageInGUI;

import java.sql.*;

public class PermissionDB {

    private static PermissionDB instance = null;

    private String url = "jdbc:sqlite:/C:\\sqlite\\sqlite-tools-win32-x86-3340000\\sqlite-tools-win32-x86-3340000\\pbx_permissions.db";
    Connection connection;
    Statement statement;

    private PermissionDB() {
        try {
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
            connection = DriverManager.getConnection(url);
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
    }

    public static PermissionDB getInstance() {
        if (instance == null)
            instance = new PermissionDB();

        return instance;
    }

    public boolean isIPgoodForCall(String ip, String callIP) {
        try {
            statement = connection.createStatement();
            String query = "select * from allow_call where ip = \"" + ip + "\""; // only ranges that this ip can call
            ResultSet resultSet = statement.executeQuery(query);

            boolean isOK = false;
            while (resultSet.next() && !isOK) {
                isOK = isInRange(callIP, resultSet);
                System.out.println("call: " + isOK + ", start_ip: " + resultSet.getString("start_ip") +
                        ", end_ip: " + resultSet.getString("end_ip"));
            }
            return isOK;
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        } finally {
            try {
                statement.close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
        }
        return false;
    }

    public boolean isIPgoodForRegistration(String ip) {
        try {
            statement = connection.createStatement();
            String query = "select * from allow_register";
            ResultSet resultSet = statement.executeQuery(query);

            boolean isOK = false;
            while (resultSet.next() && !isOK) {
                isOK = isInRange(ip, resultSet);
                System.out.println("register: " + isOK + ", start_ip: " + resultSet.getString("start_ip") +
                        ", end_ip: " + resultSet.getString("end_ip"));
            }
            return isOK;
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        } finally {
            try {
                statement.close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
        }
        return false;
    }

    public void addUserCallPermission(String ip) {
        String[] arrIP = ip.split("\\.");
        arrIP[3] = "1";
        String startIP = arrIP[0] + "." + arrIP[1] + "." + arrIP[2] + "." + arrIP[3];
        arrIP[3] = "254";
        String endIP = arrIP[0] + "." + arrIP[1] + "." + arrIP[2] + "." + arrIP[3];
        String query = "insert into allow_call (ip, start_ip, end_ip) select" +
                "'" + ip + "', '" + startIP + "', '" + endIP + "'"
                + " where not exists(" +
                "select 1 from allow_call where" +
                " ip = '" + ip + "' and start_ip = '" + startIP + "' and end_ip = '" + endIP + "')";
        try {
            statement = connection.createStatement();
            statement.executeUpdate(query);
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        } finally {
            try {
                statement.close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
        }
    }

    public void addCallPermissionManually(String userIP, String startIP, String endIP) {
        String query = "insert into allow_call (ip, start_ip, end_ip) select " +
                "'" + userIP + "', '" + startIP + "', '" + endIP + "'"
                + " where not exists(" +
                "select 1 from allow_call where" +
                " ip = '" + userIP + "' and start_ip = '" + startIP + "' and end_ip = '" + endIP + "')";
        try {
            statement = connection.createStatement();
            statement.executeUpdate(query);
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        } finally {
            try {
                statement.close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
        }
    }

    public void addRegisterPermissionManually(String startIP, String endIP) {
        String query = "insert into allow_register (start_ip, end_ip) select " +
                "'" + startIP + "', '" + endIP + "'"
                + " where not exists(" +
                "select 1 from allow_call where " +
                "start_ip = '" + startIP + "' and end_ip = '" + endIP + "')";
        try {
            statement = connection.createStatement();
            statement.executeUpdate(query);
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        } finally {
            try {
                statement.close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
        }
    }

    public void deleteCallPermission(String userIP, String startIP, String endIP) {
        String query = "delete from allow_call where" +
                " ip = '" + userIP + "' and start_ip = '" + startIP + "' and end_ip = '" + endIP + "'";
        try {
            statement = connection.createStatement();
            statement.executeUpdate(query);
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        } finally {
            try {
                statement.close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
        }
    }

    public void deleteRegisterPermission(String startIP, String endIP) {
        String query = "delete from allow_register where" +
                " start_ip = '" + startIP + "' and end_ip = '" + endIP + "'";
        try {
            statement = connection.createStatement();
            statement.executeUpdate(query);
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        } finally {
            try {
                statement.close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
        }
    }

    private boolean isInRange(String ip, ResultSet resultSet) {
        boolean isOK = false;
        int compareStart = 0;
        try {
            compareStart = compareIP(ip, resultSet.getString("start_ip"));
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        if (compareStart >= 0) {
            int compareEnd = 0;
            try {
                compareEnd = compareIP(ip, resultSet.getString("end_ip"));
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
            if (compareEnd <= 0)
                isOK = true;
        }
        return isOK;
    }

    private int compareIP(String ip1, String ip2) {
        int[] numbers1 = getIPnumbers(ip1);
        int[] numbers2 = getIPnumbers(ip2);
        for (int i = 0; i < numbers1.length; i++) {
            if (numbers1[i] == numbers2[i])
                continue;
            if (numbers1[i] > numbers2[i])
                return 1;
            else return -1;
        }
        return 0;
    }

    private int[] getIPnumbers(String ip) {
        String[] ipStrings = ip.split("\\.");
        int[] numbers = new int[ipStrings.length];
        for (int i = 0; i < ipStrings.length; i++) {
            numbers[i] = Integer.parseInt(ipStrings[i]);
        }
        return numbers;
    }

    public void showRegister(IMessageInGUI iMessageInGUI) {
        try {
            statement = connection.createStatement();
            String query = "select * from allow_register"; // only ranges that this ip can call
            ResultSet resultSet = statement.executeQuery(query);

            iMessageInGUI.printMessage("The register Permissions are:");
            while (resultSet.next()) {
                iMessageInGUI.printMessage(resultSet.getString("start_ip") + " | " + resultSet.getString("end_ip"));
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        } finally {
            try {
                statement.close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
        }
    }

    public void showCall(IMessageInGUI iMessageInGUI) {
        try {
            statement = connection.createStatement();
            String query = "select * from allow_call"; // only ranges that this ip can call
            ResultSet resultSet = statement.executeQuery(query);

            iMessageInGUI.printMessage("The call Permissions are:");
            while (resultSet.next()) {
                iMessageInGUI.printMessage(resultSet.getString("ip") + " | " +
                        resultSet.getString("start_ip") + " | " + resultSet.getString("end_ip"));
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        } finally {
            try {
                statement.close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
        }
    }
}
