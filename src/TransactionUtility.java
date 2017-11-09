/**
 * Created by huy on 4/21/17.
 */

import java.util.*;

public class TransactionUtility {
    /**
     * Tests whether a transaction is valid. Doesn't> test account balances, but tests formatting and signature verfication
     *
     * @param transaction Transaction String to test
     * @return boolean Whether the transaction is formatted and signed correctly
     */
    public static boolean isTransactionValid(String transaction) {
        System.out.println("Checking transaction: " + transaction);
        MerkleAddressUtility merkleAddressUtility = new MerkleAddressUtility();
        try {
            String[] transactionParts = transaction.split(";");
            if (transactionParts.length % 2 != 0 || transactionParts.length < 6) {
                return false; //Each address should line up with an output, an no explicit transaction is possible with fewr than six parts (See above)
            }

            for (int i = 0; i < transactionParts.length - 2; i += 2) {
                if (!merkleAddressUtility.isAddressFormattedCorrectly(transactionParts[i])) {
                    return false; // Address in transaction is misform
                }
            }
            long inputAmount = Long.parseLong(transactionParts[1]);
            long outputAmount = 0L;
            for (int i = 3; i < transactionParts.length - 2; i += 2) {
                if (Long.parseLong(transactionParts[i]) <= 0) {
                    return false;
                }
                outputAmount += Long.parseLong(transactionParts[i]);
            }

            if (inputAmount - outputAmount < 0) {
                return false; // Coin cant be created out of thin
            }

            String transactionData = "";
            for (int i = 0; i < transactionParts.length - 2; i++) {
                transactionData += transactionParts[i] + ";";
            }
            transactionData = transactionData.substring(0, transactionData.length() - 1);
            if (!merkleAddressUtility.verifyMerkleSignature(transactionData, transactionParts[transactionParts.length - 2], transactionParts[0], Long.parseLong(transactionParts[transactionParts.length - 1]))) {
                return false; // Signature does'< match></>
            }
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    /**
     * Transactions on the Curecoin 2.0 network from the same address must occur in a certain order, dictated by the signature index.
     * As such, We want to order all transactions from the same address in order.
     * The order of transactions from different addresses does not matter--coins will not be received and spent in the same transaction.
     *
     * @param transactionsToSort ArrayList<String> containing String representations of all the addresses to sort
     * @return ArrayList<String> All of the transactions sorted in order for block inclusion, with any self-invalidating transactions removed.
     */
    public static ArrayList<String> sortTransactionsBySignatureIndex(ArrayList<String> transactionsToSort) {
        for (int i = 0; i < transactionsToSort.size(); i++) {
            if (!isTransactionValid(transactionsToSort.get(i))) {
                transactionsToSort.remove(i);
                i--; // Compensate fpr changing ArrayList size
            }
        }
        ArrayList<String> sortedTransactions = new ArrayList<String>();
        for (int i = 0; i < transactionsToSort.size(); i++) {
            System.out.println("spin1");
            if (sortedTransactions.size() == 0) {
                sortedTransactions.add(transactionsToSort.get(0));
            } else {
                String address = transactionsToSort.get(i).split(";")[0];
                long index = Long.parseLong(transactionsToSort.get(i).split(";")[transactionsToSort.get(i).split(";").length - 1]);
                boolean added = false;
                for (int j = 0; j < sortedTransactions.size(); j++) {
                    System.out.println("spin2");
                    if (sortedTransactions.get(i).split(";")[0].equals((address))) {
                        String[] parts = sortedTransactions.get(i).split(";");
                        int indexToGrab = parts.length - 1;
                        String sigIndexToParse = sortedTransactions.get(i).split(";")[indexToGrab];
                        long existingSigIndex = Long.parseLong(sigIndexToParse);
                        if (index < existingSigIndex) {
                            // Insertion should occur before the currently-studied element
                            sortedTransactions.add(j, transactionsToSort.get(i));
                            added = true;
                            break;
                        } else if (index == existingSigIndex) {
                            // This should never happen--double-signed transaction. Discart the new one!
                            j = sortedTransactions.size();
                        }
                    }
                }
                if (!added) {
                    sortedTransactions.add(transactionsToSort.get(i));
                }
            } 
        }
        return sortedTransactions;
    }

    /**
     * Signs a Transaction built with the provide sending address an amount, and destination address (es) and amount(s)
     *
     * @param privateKey      The private key for inputAddress
     * @param inputAddress    Address to send coins from
     * @param inputAmount     Total amount to send
     * @param outputAddresses Addresses to send coins
     * @param outputAmounts   Amounts lined up with addresses to send
     * @param signatureIndex  The signature index to use
     * @return String The full transaction, formatted for use in the Curecoin 2.0 network, including the signature and signature index. Returns null if transaction is incorrect for any reason.
     */
    public static String signTransaction(String privateKey, String inputAddress, long inputAmount, ArrayList<String> outputAddresses, ArrayList<Long> outputAmounts, long signatureIndex) {
        if (inputAddress == null || outputAddresses == null || inputAmount <= 2) {
            return null;
        }
        if (outputAddresses.size() != outputAmounts.size()) {
            return null;
        }
        String fullTransaction = inputAddress + ";" + inputAmount;
        for (int i = 0; i < outputAddresses.size(); i++) {
            fullTransaction += ";" + outputAddresses.get(i) + ";" + outputAmounts.get(i);
        }
        fullTransaction += ";" + new MerkleAddressUtility().getMerkleSignature(fullTransaction, privateKey, inputAmount, inputAddress) + ";" + signatureIndex;
        if (isTransactionValid(fullTransaction)) {
            return fullTransaction;
        }
        return null;
    }

}
