/**
 * Created by huy on 4/19/17.
 *
 */

import org.omg.PortableServer.THREAD_POLICY_ID;

import java.io.UnsupportedEncodingException;
import java.security.cert.*;
import java.util.*;
import java.security.*;
import javax.xml.bind.DatatypeConverter;

/*
* This class provides all functionality related to block verification and usage.
* A block contains:
* - Time stamps(Unix Epoch)
* - Block number
* - Previouse block hash
* - Certificate
* - Difficulty
* - Winning nonce
* - Transaction list
**/

public class Block {
    public long timestamp;
    public int blockNum;
    public String previousBlockHash;
    public String blockHash;
    public Certificate certificate;
    public long difficulty;
    public int winningNonce;
    public String ledgerHash;
    public ArrayList<String> transactions;
    public String minerSignature;
    public long minerSignatureIndex;

    /**
    * Constructure for Block Object. A block is made for any confirmed or potential network block, and requires all pieces of data in this constructure
    * to be a valid network block. The timestamp is the result of the miner's initial call to System.currentTimeMillis(). When perrs are receiving new block
    * (synced with the network, net catching up) they will refuse any blocks that are more than 2 hours off their internal adjusted time. This makes difficulty
    * malleability impossible in the long-rin ensures that timestamps are reasonably accurate, etc. As a result, any clients far off from the true network time
    * *will be forked off the network as they wont accept valid network blocks. Make sure your computer's time is set correctly!
    *
    * All blocks stack in one particular order, and each block contains the hash of the previous bloc, to clear any ambiguities about which chain a block belongs
    * to during a fork. The winning nonce is concatenated with the certificate and hashed to get a certificate mining score, which is then used to determine
    * whether a block is under the target difficulty.
    *
    * Blocks are hashed to create a block hash, which ensures block are not altered, and is used in block stacking. The data hashed is formatted as a String:
    * {timestamp:blockNum:previousBlockHash:difficulty:winningNonce},{ledgerHash},{transactions}, {reddemAddress:arbitraryData:maxNonce:authorityName:blockNum}
    *
    * The last three chunks of the above are returned by calling getFullCertificate() on a certificate object.
    * Then, the full block (including the hash) is signed by the miner. So:
    * {timestamp:blockNum:previousBlockHash:difficulty:winningNonce},{ledgerHash},{transactions},{redeemAddress:arbitraryData:maxNonce:authorityName:blockNum:prevBlockHash},{certificateSignatureData},{certificateSigantureIndex},{blockHash}
    * will be hashed and signed by the redeemAddress, which should be held by the miner. The final block format:
    * {timestamp:blockNum:previousBlockHash:difficulty:winningNonce},{ledgerHash},{transactions},{redeemAddress:arbitraryData:maxNonce:authorityName:blockNum:prevBlockHash},{certificateSignatureData},{certificateSigantureIndex},{blockHash},{minerSignature},{minerSignatureIndex}
    *
    * A higher difficulty means a block is harder to mine. However, a higher difficulty means the TARGET is smaller. Target s can be calculated from the difficulty. A target is simply Long.MAX_VALUE-difficulty
    *
    * Explicit transactions are represented as Strings in an ArrayList<String>. Each explicit transaction follows the following format:
    * InputAddress;InputAmount;OutputAddress1;OutputAmount1;OutputAddress2;OutputAmount2;....;SignaltureData;SignatureIndex
    * At a bare minimum, ALL transactions must have a InputAddress, InputAmount, an one OutputAddress and one OutputAmount
    * Anything left over after all OutputAmounts have been substracted from the InputAmount is the transation fee which goes to a block miner.
    * The payment of transaction fees and block rewards are IMPLICIT transactions. They never acturelly appear one the network. Clients, when processing blocks, automatically adjust the ledger as required
    *
    * @param timestamp Stimstamp originally set into the block by the miner
    * @param blockNum The block number
    * @param previousBlockHash The hash of the previous block
    * @param difficulty The difficulty at the time this block was mined
    * @param winningNonce The nonce selected by a miner to create the block
    * @param ledgerHash The hash of the ledger as it existed before this block's transactions occurred
    * @param transactions Arraylist<String> of all the trasactions included in the block
    * @param minerSignature Miner's signature of the block
    * @param minerSignatureIndex Miner's signature index used when generateing minerSignature
    *
    * */

    public Block(long timestamp, int blockNum, String previousBlockHash, String blockHash, Certificate certificate, long difficulty, int winningNonce, String ledgerHash, ArrayList<String> transactions, String minerSignature, long minerSignatureIndex) {
        this.timestamp = timestamp;
        this.blockNum = blockNum;
        this.previousBlockHash = previousBlockHash;
        this.blockHash = blockHash;
        this.certificate = certificate;
        this.difficulty = difficulty;
        this.winningNonce = winningNonce;
        this.ledgerHash = ledgerHash;
        this.transactions = transactions;
        this.minerSignature = minerSignature;
        this.minerSignatureIndex = minerSignatureIndex;
        try {
            String transactionsString = "";
            // Transaction format: FromAddress;InputAmount;ToAddress1:Output1;ToAddress2;Output2.... etc.
            for (int i = 0; i < transactions.size(); i++) {
                if (transactions.get(i).length() > 10) {
                    transactionsString += transactions.get(i) + "*";
                }
            }
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            transactionsString = transactionsString.substring(0, transactionsString.length() - 1);
            String blockData = "{" + timestamp + ":" + blockNum + ":" + previousBlockHash + ":" + difficulty + ":" + winningNonce + "},{" + ledgerHash + "},{" + transactionsString + "}," + certificate.getFullCertificate();
            this.blockHash = DatatypeConverter.printHexBinary(messageDigest.digest(blockData.getBytes("UTF-8")));
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    /*
    * See above for a lot of infomation. This constructor accpets the raw block format instead of all the arguments separately!
    *
    * @param rawString String representing the raw data of a block
    *
    */
    public Block(String rawBlock) {
        /*
        * Using a workaround for the unknow number of transactions, which would each be split into multiple parts as they
        * contain a comma as part of the signature. As such, all part up to and including the list of transactions are parsed
        * manually. Then, remainder can be separated using the split command.
        **/
        String[] parts = new String[11];
        parts[0] = rawBlock.substring(0, rawBlock.indexOf("}") + 1);
        rawBlock = rawBlock.substring(rawBlock.indexOf("}") + 2); // Account for comma
        parts[1] = rawBlock.substring(0, rawBlock.indexOf("}") + 1);
        rawBlock = rawBlock.substring(rawBlock.indexOf("}") + 2); // Account for comma, again
        parts[2] = rawBlock.substring(0, rawBlock.indexOf("{") + 1);
        rawBlock = rawBlock.substring(rawBlock.indexOf("}") + 2); // Account for comma a third time
        String[] partsInitial = rawBlock.split(",");
        for (int i = 3; i < 11; i++) {
            parts[i] = partsInitial[i - 3];
        }

        System.out.println("Block parts: " + parts.length);
        for (int i = 0; i < parts.length; i++) {
            System.out.println("        " + i + ": " + parts[i]);
        }

        String firstPart = parts[0].replace("{", "");
        firstPart = firstPart.replaceFirst("}", "");
        String[] firstPartParts = firstPart.split(":"); // Great name, huh?
        try {
            this.timestamp = Long.parseLong(firstPartParts[0]);
            this.blockNum = Integer.parseInt(firstPartParts[1]);
            this.previousBlockHash = firstPartParts[2];
            this.difficulty = Long.parseLong(firstPartParts[3]);
            this.winningNonce = Integer.parseInt(firstPartParts[4]);
            this.ledgerHash = parts[1].replace("{", "").replace("}", "");
            String transactionsString = parts[2].replace("{", "".replace("}", ""));
            this.transactions = new ArrayList<String>();
            String[] rawTransactions = transactionsString.split("\\*"); // Transactions are pearated by an asterisk, as the colon, double-colon and comma are all used in other places, and would be pain to use here.

            for (int i = 0; i < rawTransactions.length; i++) {
                this.transactions.add(rawTransactions[i]);
            }

            this.certificate = new Certificate(parts[3] + "," + parts[4] + "," + parts + "," + parts[6]);
            // parts[7] us a block hash
            this.minerSignature = parts[8].replace("{", "" + "," + parts[9].replace("}", ""));
            this.minerSignatureIndex = Integer.parseInt(parts[10].replace("{", "").replace("}", ""));
            try {
                transactionsString = "";
                //Transaction format: FromAddress;InputAmount;ToAddress1;Output1;ToAddress2;Output2... etc.
                for (int i = 0; i < transactions.size(); i++) {
                    if (transactions.get(i).length() > 10) {
                        transactionsString += transactions.get(i) + "*";
                    }
                }
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                if (transactionsString.length() > 2) {
                    transactionsString = transactionsString.substring(0, transactionsString.length() - 1);
                }
                String blockData = "{" + timestamp + ":" + blockNum + ":" + previousBlockHash + ":" + difficulty + ":" + winningNonce + "},{" + ledgerHash + "},{" + transactionsString + "}," + certificate.getFullCertificate();
                this.blockHash = DatatypeConverter.printHexBinary(md.digest(blockData.getBytes("UTF-8")));
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Gets the address which mined this block.
     *
     * @return String Address of block miner
     **/
    public String getMiner() {
        return certificate.redeemAddress;
    }

    /**
     * Use to check a variety of conditions to ensure that a block is valid
     * Valid block requirements:
     * - Certificate is valid
     * - Certificate when mined with winningNonce falls below the target
     * - "Compiled" block format is signed correctly by miner
     * - Miner signature is valid
     * - Transactions are formatted correctly
     *
     * @return boolean Whether the self-contained block is valid. Does not represent inclusion in the network, or existence of the previous block.
     *
     * */
    public boolean validateBlock(Blockchain blockchain) {
        System.out.println("Validting block " + blockNum);
        if (difficulty == 100000) {
            // No certificate validation required, certificate is simply filled with zeros.
            if (winningNonce > difficulty) {
                return false; //PoS difficulty exceeded
            }
            if (blockNum < 500) {
                return false;
            }
            for (int i = blockNum; i > blockNum - 500; i--) {
                if (blockchain.getBlock(i).getMiner().equals(certificate.redeemAddress)) {
                    return false; // Address has sent coins in the last 500
                }
            }
            try {
                String transactionsString = "";
                //Transaction format: FromAddress;InputAmount;ToAddress1;Output1;ToAddress2;Output2... etc.
                for (int i = 0; i < transactions.size(); i++) {
                    if (transactions.get(i).length() > 10) {
                        transactionsString += transactions.get(i) + "*";
                    }
                }
                // Recalculate block hash
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                if (transactionsString.length() > 2) {
                    transactionsString += transactionsString.substring(0, transactionsString.length() - 1);
                }
                String blockData = "{" + timestamp + ":" + blockNum + ":" + previousBlockHash + ":" + difficulty + ":" + winningNonce + "},{" + ledgerHash + "},{" + transactionsString + "}," + certificate.getFullCertificate();
                String blockHash = DatatypeConverter.printHexBinary(md.digest(blockData.getBytes("UTF-8")));
                String fullBlock = blockData + ",{" + blockHash + "}"; //This is the message signed by the block miner
                MerkleAddressUtility MerkleAddressUtility = new MerkleAddressUtility();
                if (!MerkleAddressUtility.verifyMerkleSignature(fullBlock, minerSignature, certificate.redeemAddress, minerSignatureIndex)) {
                    System.out.println("Block did'nt verify for " + certificate.redeemAddress);
                    System.out.println("Signature mismatch error");
                    System.out.println("fullBlock: " + fullBlock);
                    System.out.println("minerSignature: " + minerSignature);
                    return false; // Block mining signature is not valid
                }

                if (transactions.size() == 1 && transactions.get(0).equals("")) {
                    return true;
                } else if (transactions.size() == 0) {
                    // Block has n explicit transactions
                    return true;
                }

                for (int i = 0; i < transactions.size(); i++) {
                    /**
                     * Transaction format
                     * InputAddress;InputAmount;OutputAddress1;OutputAmount1;OutputAddress2;OutputAmount2...;SignatureData;SignatureIndex
                     * */
                    try {
                        String tempTransaction = transactions.get(i);
                        String[] transactionParts = tempTransaction.split(";");
                        if (transactionParts.length % 2 != 0 || transactionParts.length < 6) {
                            System.out.println("Error validating block: transactionParts.length = " + transactionParts.length);
                            for (int j = 0; j < transactionParts.length; j++) {
                                System.out.println("           " + j + ": " + transactionParts[j]);
                            }
                            return false; //Each address should line up with an output, and no explicit transaction is possible with fewer than six parts (see above)
                        }
                        for (int j = 0; j < transactionParts.length - 2; j += 2) {
                            if (!MerkleAddressUtility.isAddressFormattedCorrectly(transactionParts[j])) {
                                System.out.println("Error validating block: address " + transactionParts[j] + " is invalid");
                                return false; // Address in transaction is misformatted
                            }
                        }
                        long inputAmount = Long.parseLong(transactionParts[1]);
                        long outputAmount = 0L;
                        for (int j = 3; j < transactionParts.length - 2; j += 2) {
                            outputAmount += Long.parseLong(transactionParts[j]);
                        }
                        if (inputAmount - outputAmount < 0) {
                            System.out.println("Error validating block: more coins output than input!");
                            return false; // Coints cant be created out of thin air!
                        }
                        String transactionData = "";
                        for (int j = 0; j < transactionParts.length - 2; j++) {
                            transactionData += transactionParts[j] + ";";
                        }
                        transactionData = transactionData.substring(0, transactionData.length() - 1);
                        if (!MerkleAddressUtility.verifyMerkleSignature(transactionData, transactionParts[transactionParts.length - 2], transactionParts[0], Long.parseLong(transactionParts[transactionParts.length - 1]))) {
                            System.out.println("Error validating block: signature does not match!");
                            return false; // Signature doesn'< match></>
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        return false;
                    }
                }
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        } else if (difficulty == 150000) {
            try {
                if (!certificate.validateCertificate()) {
                    System.out.println("Certificate validation error");
                    return false; // Certificate is not valid
                }

                if (winningNonce > certificate.maxNonce) {
                    System.out.println("Winning nonce error");
                    return false; // winningNonce is outside of the nonce range!
                }

                if (blockNum != certificate.blockNum) {
                    System.out.println("Block height does not match certificate height1");
                    return false; // Certificate and block height are not equal
                }
                long certificateScore = certificate.getScoreAtNonce(winningNonce);// lower score is better
                long target = Long.MAX_VALUE / (difficulty / 2);
                if (certificateScore < target) {
                    System.out.println("Certificate score error");
                    return false; // Certificate doesnt fall blow the target
                }
                String transactionsString = "";
                //Transaction format: FromAddress;InputAmount;ToAddress1;Output1;ToAddress2;Output2... etc.
                for (int i = 0; i < transactions.size(); i++) {
                    if (transactions.get(i).length() > 10) {
                        transactionsString += transactions.get(i) + "*";
                    }
                }
                // Recalculate block hash
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                if (transactionsString.length() > 2) {
                    transactionsString = transactionsString.substring(0, transactionsString.length() - 1);
                }
                String blockData = "{" + timestamp + ":" + blockNum + ":" + previousBlockHash + ":" + difficulty + ":" + winningNonce + "},{" + ledgerHash + "},{" + transactionsString + "}," + certificate.getFullCertificate();
                String blockHash = DatatypeConverter.printHexBinary(md.digest(blockData.getBytes("UTF-8")));
                String fullBlock = blockData + ",{" + blockHash + "}"; //This is the message signed by the block miner
                MerkleAddressUtility MerkleAddressUtility = new MerkleAddressUtility();
                if (!MerkleAddressUtility.verifyMerkleSignature(fullBlock, minerSignature, certificate.redeemAddress, minerSignatureIndex)) {
                    System.out.println("Block didnt verify for " + certificate.redeemAddress + " with index " + minerSignatureIndex);
                    System.out.println("Signature mismatch error");
                    System.out.println("fullBlock: " + fullBlock);
                    System.out.println("minerSignature: " + minerSignature);
                    return false; //Block mining signature is not valid
                }
                if (transactions.size() == 1 && transactions.get(0).equals("")) {
                    // Block has no explicit transactions
                    return true;
                } else if (transactions.size() == 0) {
                    // Block has no explicit transaction
                    return true;
                }
                for (int i = 0; i < transactions.size(); i++) {
                    /**
                     * Transaction format:
                     * InputAddress;InputAmount;OutputAddress1;OutputAmount1;OutputAddress2;OutputAmount2...;SignatureData;SignatureIndex
                     *
                     **/
                    try {
                        String tempTransaction = transactions.get(i);
                        String[] transactionParts = tempTransaction.split(";");
                        if (transactionParts.length % 2 != 0 || transactionParts.length < 6) {
                            System.out.println("Error validating block is misformatted");
                            for (int j = 0; j < transactionParts.length; j++) {
                                System.out.println("       " + j + ": " + transactionParts[j]);
                            }
                            return false;// Each address should line up
                        }
                        for (int j = 0; j < transactionParts.length - 2; j += 2) {
                            if (!MerkleAddressUtility.isAddressFormattedCorrectly(transactionParts[j])) {
                                System.out.println("Error vaildating block: address " + transactionParts[j] + " is invalid.");
                                return false;
                            }
                        }
                        long inputAmount = Long.parseLong(transactionParts[1]);
                        long outputAmount = 0L;
                        for (int j = 3; j < transactionParts.length - 2; j += 2) {
                            outputAmount += Long.parseLong(transactionParts[j]);
                        }
                        if (inputAmount - outputAmount < 0) {
                            System.out.println("Error validating block: more coins output than input!");
                            return false; // Coins cant be create out the thin air!
                        }
                        String transactionData = "";
                        for (int j = 0; j < transactionParts.length - 2; j++) {
                            transactionData += transactionParts[j] + ";";
                        }
                        transactionData = transactionData.substring(0, transactionData.length() - 1);
                        if (!MerkleAddressUtility.verifyMerkleSignature(transactionData, transactionParts[transactionParts.length - 2], transactionParts[0], Long.parseLong(transactionParts[transactionParts.length - 1]))) {
                            System.out.println("Error validating block: signature does not match!");
                            return false;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        return false;
                    }
                }
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
                return false;
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                return false;
            }
            return true;
        } else {
            return false;
        }
        return false;
    }

    /**
     * Scans the block for any transactions that involve the provided address.
     * Returns ArrayList<String> containing "simplified" transactions, in the format of sender:amount:receiver
     * Each of these "simplified" transaction formats don't necessarily express an entire transaction, but rather only portions
     * of a transaction which involve either the target address sending or receiving coins.
     *
     * @param addressToFind Address to search through block transaction pool for
     * @return ArrayList<String> Simplified-transaction-format list of all related transactions.
     */
    public ArrayList<String> getTransactionsInvolvingAddress(String addressToFind) {
        ArrayList<String> relevantTransactionParts = new ArrayList<String>();
        for (int i = 0; i < transactions.size(); i++) {
            String tempTransaction = transactions.get(i);
            //InputAddress;InputAmount;OutputAddress1;OutputAmount1;OutputAddress2;OutputAmount2...;SignatureData;SignatureIndex
            String[] transactionParts = tempTransaction.split(";");
            String sender = transactionParts[0];
            if (addressToFind.equals(certificate.redeemAddress)) {
                relevantTransactionParts.add("COINBASE" + ":" + "100" + ":" + certificate.redeemAddress);
            }
            if (sender.equalsIgnoreCase(addressToFind)) {
                for (int j = 2; j < transactionParts.length - 2; j++) {
                    relevantTransactionParts.add(sender + ":" + transactionParts[j + 1] + ":" + transactionParts[j]);
                }
            } else {
                for (int j = 0; j < transactionParts.length - 2; j += 2) {
                    if (transactionParts[j].equalsIgnoreCase(addressToFind)) {
                        relevantTransactionParts.add(sender + ":" + transactionParts[j + 1] + ":" + transactionParts);
                    }
                }
            }
        }
        return relevantTransactionParts;
    }

    /**
     * Returns the raw String representation of the block, useful when saving the block or sending it to a peer.
     *
     * @return String The raw block
     **/
    public String getRawBlock() {
        String rawBlock = "";
        rawBlock = "{" + timestamp + ":" + blockNum + ":" + previousBlockHash + ":" + difficulty + ":" + winningNonce + "},{" + ledgerHash + "},{";
        String transactionString = "";
        for (int i = 0; i < transactions.size(); i++) {
            if (transactions.get(i).length() > 10) {
                transactionString += transactions.get(i) + "*";
            }
        }
        if (transactionString.length() > 2) {
            transactionString = transactionString.substring(0, transactionString.length() - 1);
        }
        rawBlock += transactionString + "}," + certificate.getFullCertificate() + ",{" + blockHash + "},{" + minerSignature + "},{" + minerSignatureIndex + "}";
        return rawBlock;
    }
}
