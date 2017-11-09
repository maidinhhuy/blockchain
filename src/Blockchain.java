/**
 * Created by huy on 4/19/17.
 */

import java.io.*;
import java.util.*;

/**
* Only one Blockchain object is created per instance of the deamon. It keeps track of ALL possible chains, and internally handles chain reorganization.
* The decusion to put LedgerManager as an object owned by Blockchain was for purposes of simplicity--there's only one blockchain, and only one LeggerManager
* The Blockchain has fork management integered, and should be able to appropriately handle any unexpected circumstances. Fork management is one of the
* Major things. I'm waitching for during 2.0.0a1. If you want to try to fork the network, please do. I'm sure there are ways.
*
* As the blockchain has the most up-to-date infomation about blockchain data, it makes perfect sense for the ledger, which is based purely on the blockchain
* to be managed by the Blockchain object. Initial plans were to have separate Blockchain objects for each fork in a chain but overhead of cloning Blockchain
* objects seemed unwarranted. Significant optimazatoin still needs to be done on the fork management-climbing all the way down the shorter chain and back
* the longer chain is NOT a permanat solution, and I hope to have respetable fork management overhead by 2.0.0a2.
*
* Additionally, there is no need to store indentical blocks between multiple forks. The overhead of a fork suddenly requireing double the blockchain storage
* unacceptable. It's works, I think. But unacceptable for production code, so that'll certainly change, hopefully by 2.0.0a2 or 2.0.0a3, depending on my schedule
*
* As blocks are added to the blockchain, the ledger is updated. While working beautifully in small-scale testing, I'm sure the signature count synchronization
* between signed transactions and blocks will trip up at some point, and send the Blockchain object into either a loop of dispair, or an irrecoverable error.
* Either is equally frightening.
*
* Fault tolerance with desynchronization between ledger and blockchain for signature accounts will be a 2.0.0a3 or 2.0.0a4 feature. I've gotta think long and
* hard about the best approaches that don't compromise security in the name of fault-tolerance, while remaining usable, reliable, and fast.
*
* You'll notice a loop which appears to retry transactions up to 10,000 times. Transactions must be executed in order--two transactions from the same address
* need to go in order of signature index, otherwise the storage space required to maintain all used signature indexes would be astronomical.
*
* As a caveat to the Merkle Tree signature scheme, Lamport Key reuse creates the potential for double-spend attacks, so once a Lamport Keypair is used, the network
* rejects all future signatures from that keypair. Important to keep this in mind--
* */
public class Blockchain {
    ArrayList<ArrayList<Block>> chains = new ArrayList<ArrayList<Block>>();

    private ArrayList<Block> blockQueue;

    public LedgerManager ledgerManager;

    private String dbFolder;

    private boolean gotGenesisBlock = false;

    /**
    * Constructor for Blockchain object. A Blockchain object represents an entire chain of blocks. Only one is created
    * in the entire execution of the program. All blocks will be added individually and in-order.
    *
    * This blockchain object will handle all forks--it keeps a record of forks for ten blocks. After a fork falls more than 10 blocks behind, it is deleted.
    *
    * Block are stored in a 2D ArrayList, where one demension is the blockchain 'version' (forks), and the other dimension is the block
    * @param dbFolder Folder for database to be contaioned inside.
    **/

    public Blockchain(String dbFolder) {
        this.dbFolder = dbFolder;
        this.ledgerManager = new LedgerManager(dbFolder + "/AccountBalance.bal");
        this.blockQueue = new ArrayList<Block>();
    }

    public int getBlockchainLength() {
        int longestChain = 0;
        for (int i = 0; i < chains.size(); i++) {
            if (chains.get(i).size() > longestChain) {
                longestChain = chains.get(i).size();
            }
        }

        return longestChain;
    }

    /**
    * Returns the difficulty of the latest block in the longest chain.
    *
    * @return long Curreny difficulty on longest chain
    **/
    public long getDifficulty() {
        int longestChainIndex = -1;
        int longestChainSize = -1;
        for (int i = 0; i < chains.size(); i++) {
            if (chains.get(i).size() > longestChainSize) {
                longestChainSize = chains.get(i).size();
                longestChainIndex = i;
            }
        }
        return chains.get(longestChainIndex).get(longestChainSize - 1).difficulty;
    }

    /**
    * This method is called periodically to attempt to add all queued block to the blockchain
    **/

    public void tryBlockQueue() {
        boolean addedABlock = false;
        do
        { // Some block in the queue may be attempted before other dependency blocks, so while we are able to add blocks, we will continue to add them.
            addedABlock = false;
            for (int i = 0; i < blockQueue.size(); i++) {
                if (blockQueue.get(i).validateBlock(this)) {
                    if (addBlock(blockQueue.get(i), false)) {
                        addedABlock = true;
                        blockQueue.remove(i);
                        i--; // Compenstate for changing ArrayList size, don't> want to skip an element!
                    }
                } else {
                    blockQueue.remove(i);
                }
            }
        } while (addedABlock);
    }

    /**
    * Retreves the block at blockNum (starting from 0) from the longest chain.
    *
    * @param blockNum The block number to retrieve
    *
    * @return Block Block at blockNum in longest chain
    **/
    public Block getBlock(int blockNum) {
        int longestChainLength = 0;
        int longestChain = -1;
        for (int i = 0; i < chains.size(); i++) {
            if (chains.get(i).size() > longestChainLength) {
                longestChain = i;
                longestChainLength = chains.get(i).size();
            }
        }
        return chains.get(longestChain).get(blockNum);
    }

    /**
    * This method attempts to a block to the blockchain. No upstream handling is required to make sure te block is valid,
    * all of that is handle here. Additionally, the block will be automatically placed onto the correct fork, or a new fork will be made if necessary
    *
    * @param block Block to add to the blockchain
    * @param fromBlockchainFile Whether the added block was read in from file; aka !fromBlockchainFile is whether to write it back out to the db.
    *
    * @return boolean Whether adding the block was unsuccessful. Most common source of returning false is a block that doesn'< verify></>*/
    public boolean addBlock(Block block, boolean fromBlockchainFile) {
        System.out.println("Attempting to add block " + block.blockNum + " with hash " + block.blockHash);
        try {
            boolean isPOS = false;
            if (block.difficulty == 100000) {
                isPOS = true;
            }
            if (block.difficulty != 150000 || !isPOS) {
                System.out.println("Block detected with wrong difficulty");
                return false;
            }

            // A bit of cleanup--remove chain that are more than 10 blocks shorter than the largest chain.
            int largestChainLength = 0;
            ArrayList<Block> largestChain = new ArrayList<Block>();
            String largestChainLastBlockHash = "";
            for (int i = 0; i < chains.size(); i++) {
                if (chains.get(i).size() > largestChainLength) {
                    largestChain = chains.get(i);
                    largestChainLength = chains.get(i).size();
                    largestChainLastBlockHash = chains.get(i).get(chains.get(i).size() - 1).blockHash;
                }
            }

            // Now we have the longest chain, we remove any chains that are less than largestChainLength - 10
            for (int i = 0; i < chains.size(); i++) {
                if (chains.get(i).size() < largestChainLength) {
                    chains.remove(i);
                    i--;
                }
            }
            if (!block.validateBlock(this)) {
                return false; // Block is not a valid block
            }

            //Block looks fine on its own--we don't know how it's going to play with the chain. If the block's number is larger than the largest chain + 1, we'll put the block in a queue to attempt to add later.
            // Block numbering start at 0.
            if (block.blockNum > largestChainLength) {
                // Add it to the queue.
                blockQueue.add(block);
                /*
                 * In the future, the addBlock() method may be changed to return an int, with values representing things like block above existing heights, validation error, block not on any chains, etc.
                 * For now, the boolean indicates simply whether immediate addition of the block to some internal blockchain was successful.
                 */
                System.out.println("Block " + block.blockNum + " with starting hash " + block.blockHash.substring(0, 20) + " added to queue...");
                System.out.println("LargestChainLength: " + largestChainLength);
                System.out.println("block.blockNum: " + block.blockNum);
                return false;
            }

            // If no chains exits and this is the first block, we'll create our first chain:
            if (!gotGenesisBlock) {
                gotGenesisBlock = true;
                chains.add(new ArrayList<Block>());
                largestChain = chains.get(0);
                largestChainLastBlockHash = block.blockHash;
                if (ledgerManager.lastBlockNum < 0) {
                    //We can't directly assign transactionsToApply to block.transactions as we are going to edit it, and we don't want to delete transactions from the actual block.
                    ArrayList<String> tranasctionsToApply = new ArrayList<String>();
                    for (int i = 0; i < block.transactions.size(); i++) {
                        tranasctionsToApply.add(block.transactions.get(i));
                    }
                    int loopCount = 0;
                    int transactionsApplied = 0;
                    // Yippee let's add our first chunk of transactions if we need to!
                    while (tranasctionsToApply.size() > transactionsApplied && !tranasctionsToApply.get(0).equals("")) {
                        loopCount++;
                        for (int k = 0; k < tranasctionsToApply.size(); k++) {
                            if (ledgerManager.executeTransaction(tranasctionsToApply.get(k))) {
                                tranasctionsToApply.remove(k); // Executed correctly
                                k--; // Compensate for changed ArrayList size
                            }
                        }
                        if (loopCount > 10000) {
                            System.out.println("Infinite block detected! Hash: " + block.blockHash + " and height: " + block.blockNum);
                            System.out.println(tranasctionsToApply.size());
                            System.exit(-1);
                        }
                    }
                }

                ledgerManager.adjustAddressBalance(chains.get(0).get(0).certificate.redeemAddress, 100); //Pay mining fee
                ledgerManager.adjustAddressSignatureCount(chains.get(0).get(0).certificate.redeemAddress, 1);
                if (!fromBlockchainFile) {
                    writeBlockToFile(block);
                }
                return true;
            }

            // Inititally, check for duplicate blocks
            for (int i = 0; i < chains.size(); i++) {
                if (chains.get(i).get(chains.get(i).size() - 1).blockHash.equals(block.blockHash)) {
                    // Duplicate bloc; block has already bean added. This happens all the time, as multiple peers all broadcast the same block
                    System.out.println("Duplicate block received from peer");
                    return false;
                }
            }

            // Then, we will see whether it goes well onto the ends of any existing chains
            for (int i = 0; i < chains.size(); i++) {
                // Block numbering starts at 0
                if (chains.get(i).get(chains.get(i).size() - 1).blockHash.equals(block.previousBlockHash) && chains.get(i).size() == block.blockNum) {
                    chains.get(i).add(block);
                    if (chains.get(i).size() > largestChainLength) {
                        if (!chains.get(i).get(chains.get(i).size() - 2).blockHash.equals(largestChainLastBlockHash)) {
                            //Future implementations will be MUCH more efficient--they'll reverse down the fork and ride it back up.
                            //However, during the developmental time squeeze that is two hours before 2.0.0a1 launch when I realized the logic I had here wasn't good, this seemed like a great idea.
                            for (int j = largestChain.size(); j > 0; j--) {
                                ArrayList<String> transactionsToReverse = largestChain.get(j).transactions;
                                for (int k = 0; k < transactionsToReverse.size(); k++) {
                                    ledgerManager.reverseTransaction(transactionsToReverse.get(k));
                                }
                                ledgerManager.adjustAddressBalance(largestChain.get(j).certificate.redeemAddress, -100);
                                ledgerManager.adjustAddressSignatureCount(largestChain.get(j).certificate.redeemAddress, -1);
                            }
                            //The ledger is completely empty, basically. Good job. Efficiency at its finest. I WILL FIX THIS
                            for (int j = 0; j < chains.get(i).size(); j++) {
                                //We can't directly assign transactionsToApply to block.transactions as we are going to edit it, and we don't want to delete transactions from the actual block.
                                ArrayList<String> transactionsToApply = new ArrayList<String>();
                                for (int k = 0; k < block.transactions.size(); k++) {
                                    transactionsToApply.add(block.transactions.get(k));
                                }
                                int loopCount = 0;
                                while (transactionsToApply.size() > 0) {
                                    loopCount++;
                                    for (int k = 0; k < transactionsToApply.size(); k++) {
                                        System.out.println("Attempting to execute transaction: " + transactionsToApply.get(k).substring(0, 45) + "..." + transactionsToApply.get(k).substring(transactionsToApply.get(k).length() - 20));
                                        if (ledgerManager.executeTransaction(transactionsToApply.get(k))) {
                                            System.out.println("Successfully executed transaction!");
                                            transactionsToApply.remove(k); // Executed correctly
                                            k--; //Compensate for changed ArrayList size
                                        } else {
                                            System.out.println("Didnt execute transaction...");
                                        }
                                    }
                                    if (loopCount > 10000) {
                                        System.out.println("Infinite block detected! Hash: " + chains.get(i).get(j).blockHash + " and height: " + chains.get(i).get(j).blockNum);
                                        System.exit(-1);
                                    }
                                }
                                ledgerManager.adjustAddressBalance(chains.get(i).get(j).certificate.redeemAddress, 100);
                                ledgerManager.adjustAddressSignatureCount(chains.get(i).get(j).certificate.redeemAddress, 1);

                            }
                        } else {
                            // We need to execute all the transactions....
                            if (ledgerManager.lastBlockNum < block.blockNum) {
                                //We can't directly assign transactionsToApply to block.transactions as we are going to edit it, and we don't want to delete transactions from the actual block.
                                ArrayList<String> transactionsToApply = new ArrayList<String>();
                                for (int k = 0; k < block.transactions.size(); k++) {
                                    transactionsToApply.add(block.transactions.get(k));
                                }
                                int loopCount = 0;
                                int completedTransactions = 0;
                                while (transactionsToApply.size() > completedTransactions) {
                                    loopCount++;
                                    for (int k = 0; k < transactionsToApply.size(); k++) {
                                        if (ledgerManager.executeTransaction(transactionsToApply.get(k))) {
                                            transactionsToApply.remove(k);
                                            k--;
                                        } else if (transactionsToApply.get(k).equals("")) {
                                            completedTransactions++;
                                        }
                                    }
                                    if (loopCount > 10000) {
                                        System.out.println("Infinite block detected! Hash: " + block.blockHash + " and height: " + block.blockNum);
                                        System.exit(-1);
                                    }
                                }
                                ledgerManager.adjustAddressBalance(block.certificate.redeemAddress, 100);
                                ledgerManager.adjustAddressSignatureCount(block.certificate.redeemAddress, 1);

                            }
                        }
                    }
                    if (!fromBlockchainFile) {
                        writeBlockToFile(block);
                    }
                    return true;
                } else {
                    System.out.println("Something went wrong with stacking...");
                    System.out.println(chains.get(i).get(chains.get(i).size() - 1).blockHash);
                    System.out.println(chains.get(i).get(chains.get(i).size() - 1).blockHash);
                }
            }
            boolean foundPlaceForBlock = false;
            for (int i = 0; i < chains.size(); i++) {
                ArrayList<Block> tempChain = chains.get(i);
                for (int j = tempChain.size() - 11; j < tempChain.size(); j++) {
                    if (j < 0) {
                        j = 0;
                    }
                    if (tempChain.get(j).blockHash.equals(block.previousBlockHash)) {
                        ArrayList<Block> newChain = new ArrayList<Block>();
                        for (int k = 0; k < j; k++) {
                            newChain.add(tempChain.get(k));
                        }
                        newChain.add(block);
                        foundPlaceForBlock = true;
                        j = tempChain.size();
                        i = chains.size();
                    }
                }
            }
            if (foundPlaceForBlock) {
                // Might be stuff here in the future...
            } else {
                // Didnt fit on nay existing blockchain
                System.out.println("Block didnt fit!");
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (!fromBlockchainFile) {
            writeBlockToFile(block);
        }
        return true;
    }

    /**
     * Writes a block to the blockchain file
     *
     * @return boolean Wheter write was successful
     **/
    public boolean writeBlockToFile(Block block) {
        System.out.println("Writing a block to file...");
        try (FileWriter fileWriter = new FileWriter(dbFolder + "/blockchain.dta", true);
             BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
             PrintWriter out = new PrintWriter(bufferedWriter)) {
            out.print(block.getRawBlock());
        } catch (Exception e) {
            System.out.println("ERROR: UNABLE TO SAVE BLOCK TO DATABASE!");
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * Saves entire blockchain to a file, useful to save the state of the blockchain so it doesn't have to be redownloaded later.
     * Blockchain is stored to a file called "blockchain.dta" inside the provided dbFolder.
     *
     * @param dbFolder Folder to save blockchain file in
     * @return boolean Whether saving to file was successful.
     */
    public boolean saveToFile(String dbFolder) {
        try {
            PrintWriter out = new PrintWriter(new File(dbFolder + "/blockchain.dta"));
            for (int i = 0; i < chains.size(); i++) {
                for (int j = 0; j < chains.get(i).size(); j++) {
                    out.println(chains.get(i).get(j).getRawBlock());
                }
            }
            out.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            System.out.println("[CRITICAL ERROR] UNABLE TO WRITE BLOCKCHAIN FILE \"" + dbFolder + "blockchain.dta!");
            return false;
        }
        return true;
    }

    /**
     * Calls getTransactionsInvolvingAddress() on all Block objects in the current Blockchain to get all relevant transactions.
     *
     * @param addressToFind Address to search through all block transaction pools for
     * @return ArrayList<String> All transactions in simplified form blocknum:sender:amount:receiver of
     */
    public ArrayList<String> getAllTransactionsInvolvingAddress(String addressToFind) {
        int longestChainLength = 0;
        int longestChainNum = -1;
        for (int i = 0; i < chains.size(); i++) {
            if (chains.get(i).size() > longestChainLength) {
                longestChainNum = i;
                longestChainLength = chains.get(i).size();
            }
        }

        ArrayList<Block> longestChain = chains.get(longestChainNum);

        ArrayList<String> allTransactions = new ArrayList<String>();

        for (int i = 0; i < longestChain.size(); i++) {
            ArrayList<String> transactionsFromBlock = longestChain.get(i).getTransactionsInvolvingAddress(addressToFind);
            for (int j = 0; j < transactionsFromBlock.size(); j++) {
                allTransactions.add(longestChain.get(i).blockNum + ":" + transactionsFromBlock.get(j));
            }
        }
        return allTransactions;
    }

    /**
     * Passthrough method to the LedgerManager object.
     *
     * @param address Address to check balance of
     * @return long Balance of address
     */
    public long getAddressBalance(String address) {
        return ledgerManager.getAddressBalance(address);
    }
}
