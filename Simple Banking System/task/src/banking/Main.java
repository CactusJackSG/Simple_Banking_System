package banking;


import java.sql.*;
import java.util.Scanner;
import java.util.concurrent.ThreadLocalRandom;

enum SystemState {
    EXIT,
    LOG_OUT,
    LOG_IN;
}

class Card {
    String number;
    String pin;
    int balance;

    private String generateLastDigit(String number) {
        int[] numberWithoutLastDigit = new int[15];
        for (int i = 0; i < numberWithoutLastDigit.length; i++) {
            numberWithoutLastDigit[i] = Integer.parseInt(String.valueOf(number.charAt(i)));
        }
        int sum = 0;

        //Luhn algorithm
        for (int i = 0; i < numberWithoutLastDigit.length; i++) {
            if (i % 2 == 0) {
                numberWithoutLastDigit[i] *= 2;
                if (numberWithoutLastDigit[i] > 9) {
                    numberWithoutLastDigit[i] -= 9;
                }
            }
            sum += numberWithoutLastDigit[i];
        }
        number += sum % 10 == 0 ? 0 : 10 - sum % 10;
        return number;
    }

    public Card createCard(Connection connection) {
        generateCardNumber();
        generateCardPin();
        String sql = "INSERT INTO card (number, pin) VALUES(?, ?)";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)){
            preparedStatement.setString(1, getNumber());
            preparedStatement.setString(2,getPin());
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return this;
    }

    private void generateCardNumber() {
        number = "400000" + String.format("%09d",ThreadLocalRandom.current().nextLong(999999999L));
        number = generateLastDigit(number);
    }

    private void generateCardPin() {
        pin = String.format("%04d", ThreadLocalRandom.current().nextInt(9999));
    }

    public boolean isAuthorized(String number, String pin) {
        return number.equals(this.number) && pin.equals(this.pin);
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public String getPin() {
        return pin;
    }

    public void setPin(String pin) {
        this.pin = pin;
    }

    public int getBalance() {
        return balance;
    }

    public void setBalance(int balance) {
        this.balance = balance;
    }

    public static Card getFromDb(String number, Connection connection) {
        String sql = "SELECT * FROM card WHERE number = ? LIMIT 1";
        Card card = new Card();
        try (PreparedStatement statement = connection.prepareStatement(sql);) {
            statement.setString(1, number);
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                card.setNumber(resultSet.getString("number"));
                card.setPin(resultSet.getString("pin"));
                card.setBalance(resultSet.getInt("balance"));
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return card;
    }

    public void deleteCard(Connection connection) {
        String sql = "DELETE FROM card WHERE number = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)){
            preparedStatement.setString(1, getNumber());
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public void addIncome(int income, Connection connection) {
        String sql = "UPDATE card SET balance = ? WHERE number = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setInt(1, income + getBalance());
            preparedStatement.setString(2, number);
            setBalance(getBalance() + income);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public String doTransfer(Connection connection, String number, Scanner scanner) {
        if (getNumber().equals(number)) {
            return "You can't transfer money to the same account!";
        }
        if (number.charAt(number.length() - 1) != generateLastDigit(number.substring(0, number.length() - 1)).charAt(number.length() - 1)) {
            return "Probably you made a mistake in the card number. Please try again!";
        }
        Card toCard = getFromDb(number, connection);
        if (toCard.getNumber() != null && !toCard.getNumber().equals(number) || toCard.getNumber() == null) {
            return "Such a card does not exist.";
        }
        System.out.println("Enter how much money you want to transfer:");
        int transferMoney = scanner.nextInt();
        if (transferMoney > getBalance()) {
            return "Not enough money!";
        }
        try {
            String sql = "UPDATE card SET balance = ? WHERE number = ?";
            connection.setAutoCommit(false);
            PreparedStatement preparedStatementFrom = connection.prepareStatement(sql);
            PreparedStatement preparedStatementTo = connection.prepareStatement(sql);
            preparedStatementTo.setInt(1, toCard.getBalance() + transferMoney);
            preparedStatementTo.setString(2, number);
            preparedStatementFrom.setInt(1, getBalance() - transferMoney);
            preparedStatementFrom.setString(2, getNumber());
            preparedStatementFrom.executeUpdate();
            preparedStatementTo.executeUpdate();
            connection.commit();
            setBalance(getBalance() - transferMoney);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return "Success!";
    }

    @Override
    public String toString() {
        return String.format("Your card number:%n%s%nYour card PIN:%n%s%n", number,pin);
    }
}

public class Main {

    private static String getMenu(SystemState state) {
        if (state == SystemState.LOG_IN) {
            return "1. Balance\n" +
                    "2. Add income\n" +
                    "3. Do transfer\n" +
                    "4. Close account\n" +
                    "5. Log out\n" +
                    "0. Exit";
        } else if (state == SystemState.LOG_OUT) {
            return "1. Create an account\n" +
                    "2. Log into account\n" +
                    "0. Exit";
        }
        return "";
    }

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("-fileName")) {
            String dbUrl;
            dbUrl = "jdbc:sqlite:" + args[1];
            String createDb = "CREATE TABLE IF NOT EXISTS card (\n"
                    + "	id INTEGER,\n"
                    + "	number TEXT,\n"
                    + "	pin TEXT,\n"
                    + "balance INTEGER DEFAULT 0\n"
                    + ");";
            try (Connection connection = DriverManager.getConnection(dbUrl);
                 Statement statement = connection.createStatement()) {
                statement.execute(createDb);
                // for creating DB
                Scanner scanner = new Scanner(System.in);
                SystemState state = SystemState.LOG_OUT;
                Card card = new Card();
                while (state != SystemState.EXIT) {
                    System.out.println(getMenu(state));
                    int input = scanner.nextInt();
                    if (state == SystemState.LOG_IN) {
                        switch (input) {
                            case 1:
                                System.out.printf("Balance: %d%n", card.getBalance());
                                break;
                            case 2:
                                System.out.println("Enter income:");
                                card.addIncome(scanner.nextInt(), connection);
                                System.out.println("Income was added!");
                                break;
                            case 3:
                                System.out.println("Transfer");
                                System.out.println("Enter card number:");
                                System.out.println(card.doTransfer(connection, scanner.next(), scanner));
                                break;
                            case 4:
                                card.deleteCard(connection);
                                card = new Card();
                                System.out.println("The account has been closed!");
                                state = SystemState.LOG_OUT;
                                break;
                            case 5:
                                System.out.println("You have successfully logged out!");
                                card = new Card();
                                state = SystemState.LOG_OUT;
                                break;
                            case 0:
                                state = SystemState.EXIT;
                                break;
                        }
                    } else if (state == SystemState.LOG_OUT) {
                        switch (input) {
                            case 1:
                                card.createCard(connection);
                                System.out.println(card);
                                break;
                            case 2:
                                System.out.println("Enter your card number:");
                                String number = scanner.next();
                                System.out.println("Enter your PIN:");
                                String pin = scanner.next();
                                card = Card.getFromDb(number, connection);
                                if (card.isAuthorized(number, pin)) {
                                    System.out.println("You have successfully logged in!");
                                    state = SystemState.LOG_IN;
                                } else {
                                    System.out.println("Wrong card number or PIN!");
                                }
                                break;
                            case 0:
                                state = SystemState.EXIT;
                                break;
                        }
                    }
                }
                System.out.println("Bye!");
            } catch (SQLException e) {
                System.out.println(e.getMessage());
            }
        }

    }
}