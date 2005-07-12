/**
 * Copyright 2005 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nutch.ndfs;

import org.apache.nutch.io.*;
import org.apache.nutch.ipc.*;
import org.apache.nutch.util.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.logging.*;

/**********************************************************
 * DataNode controls just one critical table:
 *   block-> BLOCK_SIZE stream of bytes
 *
 * This info is stored on disk (the NameNode is responsible for
 * asking other machines to replicate the data).  The DataNode
 * reports the table's contents to the NameNode upon startup
 * and every so often afterwards.
 *
 * @author Mike Cafarella
 **********************************************************/
public class DataNode implements FSConstants {
    public static final Logger LOG = LogFormatter.getLogger("org.apache.nutch.ndfs.DataNode");
    //
    // REMIND - mjc - I might bring "maxgigs" back so user can place 
    // artificial  limit on space
    //private static final long GIGABYTE = 1024 * 1024 * 1024;
    //private static long numGigs = NutchConf.get().getLong("ndfs.datanode.maxgigs", 100);
    //

    //
    // Eventually, this constant should be computed dynamically using 
    // load information
    //
    private static final int MAX_BLOCKS_PER_ROUNDTRIP = 3;

    /**
     * Util method to build socket addr from string
     */
    public static InetSocketAddress createSocketAddr(String s) throws IOException {
        String target = s;
        int colonIndex = target.indexOf(':');
        if (colonIndex < 0) {
            throw new RuntimeException("Not a host:port pair: " + s);
        }
        String host = target.substring(0, colonIndex);
        int port = Integer.parseInt(target.substring(colonIndex + 1));

        return new InetSocketAddress(host, port);
    }

    DatanodeProtocol namenode;
    FSDataset data;
    String localName;
    Vector receivedBlockList = new Vector();

    /**
     * Create using configured defaults and dataDir.
     */
    public DataNode(String datadir) throws IOException {
        this(InetAddress.getLocalHost().getHostName(), 
             new File(datadir),
             createSocketAddr(NutchConf.get().get("fs.default.name", "local")));
    }

    /**
     * Needs a directory to find its data (and config info)
     */
    public DataNode(String machineName, File datadir, InetSocketAddress nameNodeAddr) throws IOException {
        this.namenode = (DatanodeProtocol) RPC.getProxy(DatanodeProtocol.class, nameNodeAddr);
        this.data = new FSDataset(datadir);

        ServerSocket ss = null;
        int tmpPort = 7000;
        while (ss == null) {
            try {
                ss = new ServerSocket(tmpPort);
                LOG.info("Opened server at " + tmpPort);
            } catch (IOException ie) {
                LOG.info("Could not open server at " + tmpPort + ", trying new port");
                tmpPort++;
            }
        }
        this.localName = machineName + ":" + tmpPort;
        new Daemon(new DataXceiveServer(ss)).start();
    }

    /**
     */
    public String getNamenode() {
        //return namenode.toString();
	return "<namenode>";
    }

    /**
     * Main loop for the DataNode.  Runs until shutdown.
     */
    public void offerService() throws Exception {
        long wakeups = 0;
        long lastHeartbeat = 0, lastBlockReport = 0;
        long sendStart = System.currentTimeMillis();
        int heartbeatsSent = 0;

        //
        // Now loop for a long time....
        //
        boolean shouldRun = true;
        while (shouldRun) {
            long now = System.currentTimeMillis();

            //
            // Every so often, send heartbeat or block-report
            //
            synchronized (receivedBlockList) {
                if (now - lastHeartbeat > HEARTBEAT_INTERVAL) {
                    //
                    // All heartbeat messages include following info:
                    // -- Datanode name
                    // -- data transfer port
                    // -- Total capacity
                    // -- Bytes remaining
                    //
		    LOG.info("Sending heartbeat from " + localName);
                    namenode.sendHeartbeat(localName, data.getCapacity(), data.getRemaining());
                    lastHeartbeat = now;
		}
		if (now - lastBlockReport > BLOCKREPORT_INTERVAL) {
                    //
                    // Send latest blockinfo report if timer has expired
                    //
                    namenode.blockReport(localName, data.getBlockReport());
                    lastBlockReport = now;
		}
		if (receivedBlockList.size() > 0) {
                    //
                    // Send newly-received blockids to namenode
                    //
                    Block blockArray[] = (Block[]) receivedBlockList.toArray(new Block[receivedBlockList.size()]);
                    receivedBlockList.removeAllElements();
                    namenode.blockReceived(localName, blockArray);
                }

                //
                // Check to see if there are any block-instructions from the
                // namenode that this datanode should perform.
                //
                BlockCommand cmd = namenode.getBlockwork(localName);
                if (cmd.transferBlocks()) {
                    //
                    // Send a copy of a block to another datanode
                    //
                    Block blocks[] = cmd.getBlocks();
                    DatanodeInfo xferTargets[][] = cmd.getTargets();

                    for (int i = 0; i < blocks.length; i++) {
                        if (!data.isValidBlock(blocks[i])) {
                            String errStr = "Can't send invalid block " + blocks[i];
                            LOG.info(errStr);
                            namenode.errorReport(localName, errStr);
                            break;
                        } else {
                            if (xferTargets[i].length > 0) {
                                LOG.info("Starting thread to transfer block " + blocks[i] + " to " + xferTargets[i]);
                                new Daemon(new DataTransfer(xferTargets[i], blocks[i])).start();
                            }
                        }
                    }
                } else if (cmd.invalidateBlocks()) {
                    //
                    // Some local block(s) are obsolete and can be 
                    // safely garbage-collected.
                    //
                    data.invalidate(cmd.getBlocks());
                }


                //
                // There is no work to do;  sleep until hearbeat timer elapses, 
                // or work arrives, and then iterate again.
                //
                long waitTime = HEARTBEAT_INTERVAL - (now - lastHeartbeat);
                if (waitTime > 0 && receivedBlockList.size() == 0) {
                    try {
                        receivedBlockList.wait(waitTime);
                    } catch (InterruptedException ie) {
                    }
                }
            }
        }
    }

    /**
     * Server used for receiving/sending a block of data
     */
    class DataXceiveServer implements Runnable {
        ServerSocket ss;
        public DataXceiveServer(ServerSocket ss) {
            this.ss = ss;
        }

        /**
         */
        public void run() {
            try {
                while (true) {
                    Socket s = ss.accept();
                    new Daemon(new DataXceiver(s)).start();
                }
            } catch (IOException ie) {
                LOG.info("Exiting DataXceiveServer due to " + ie.toString());
            }
        }
    }

    /**
     * Thread for processing incoming/outgoing data stream
     */
    class DataXceiver implements Runnable {
        Socket s;
        public DataXceiver(Socket s) {
            this.s = s;
        }

        /**
         */
        public void run() {
            try {
                DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
                try {
                    byte op = (byte) in.read();
                    if (op == OP_WRITE_BLOCK) {
                        //
                        // Read in the header
                        //
                        DataOutputStream reply = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
                        try {
                            Block b = new Block();
                            b.readFields(in);
                            int numTargets = in.readInt();
                            if (numTargets <= 0) {
                                throw new IOException("Mislabelled incoming datastream.");
                            }
                            DatanodeInfo targets[] = new DatanodeInfo[numTargets];
                            for (int i = 0; i < targets.length; i++) {
                                DatanodeInfo tmp = new DatanodeInfo();
                                tmp.readFields(in);
                                targets[i] = tmp;
                            }
                            byte encodingType = (byte) in.read();
                            long len = in.readLong();

                            //
                            // Make sure curTarget is equal to this machine
                            // REMIND - mjc
                            //
                            DatanodeInfo curTarget = targets[0];

                            //
                            // Open local disk out
                            //
                            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(data.writeToBlock(b)));
                            InetSocketAddress mirrorTarget = null;
                            try {
                                //
                                // Open network conn to backup machine, if 
                                // appropriate
                                //
                                DataInputStream in2 = null;
                                DataOutputStream out2 = null;
                                if (targets.length > 1) {
                                    // Connect to backup machine
                                    mirrorTarget = createSocketAddr(targets[1].getName().toString());
                                    try {
                                        Socket s = new Socket(mirrorTarget.getAddress(), mirrorTarget.getPort());
                                        out2 = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
                                        in2 = new DataInputStream(new BufferedInputStream(s.getInputStream()));

                                        // Write connection header
                                        out2.write(OP_WRITE_BLOCK);
                                        b.write(out2);
                                        out2.writeInt(targets.length - 1);
                                        for (int i = 1; i < targets.length; i++) {
                                            targets[i].write(out2);
                                        }
                                        out2.write(encodingType);
                                        out2.writeLong(len);
                                    } catch (IOException ie) {
                                        if (out2 != null) {
                                            try {
                                                out2.close();
                                                in2.close();
                                            } catch (IOException out2close) {
                                            } finally {
                                                out2 = null;
                                                in2 = null;
                                            }
                                        }
                                    }
                                }

                                //
                                // Process incoming data, copy to disk and
                                // maybe to network.
                                //
                                try {
                                    boolean anotherChunk = true;
                                    byte buf[] = new byte[2048];

                                    while (anotherChunk) {
                                        while (len > 0) {
                                            int bytesRead = in.read(buf, 0, Math.min(buf.length, (int) len));
                                            if (bytesRead >= 0) {
                                                out.write(buf, 0, bytesRead);
                                                if (out2 != null) {
                                                    try {
                                                        out2.write(buf, 0, bytesRead);
                                                    } catch (IOException out2e) {
                                                        //
                                                        // If stream-copy fails, continue 
                                                        // writing to disk.  We shouldn't 
                                                        // interrupt client write.
                                                        //
                                                        try {
                                                            out2.close();
                                                            in2.close();
                                                        } catch (IOException out2close) {
                                                        } finally {
                                                            out2 = null;
                                                            in2 = null;
                                                        }
                                                    }
                                                }
                                            }
                                            len -= bytesRead;
                                        }

                                        if (encodingType == RUNLENGTH_ENCODING) {
                                            anotherChunk = false;
                                        } else if (encodingType == CHUNKED_ENCODING) {
                                            len = in.readLong();
                                            if (out2 != null) {
                                                out2.writeLong(len);
                                            }
                                            if (len == 0) {
                                                anotherChunk = false;
                                            }
                                        }
                                    }

                                    if (out2 == null) {
                                        LOG.info("Received block " + b + " from " + s.getInetAddress());
                                    } else {
                                        out2.flush();
                                        long complete = in2.readLong();
                                        if (complete != WRITE_COMPLETE) {
                                            LOG.info("Conflicting value for WRITE_COMPLETE: " + complete);
                                        }
                                        LOG.info("Received block " + b + " from " + s.getInetAddress() + " and mirrored to " + mirrorTarget);
                                    }
                                } finally {
                                    if (out2 != null) {
                                        out2.close();
                                        in2.close();
                                    }
                                }
                            } finally {
                                out.close();
                            }
                            data.finalizeBlock(b);

                            // 
                            // Tell the namenode that we've received this block 
                            // in full.
                            //
                            synchronized (receivedBlockList) {
                                receivedBlockList.add(b);
                                receivedBlockList.notifyAll();
                            }

                            //
                            // Tell client job is done
                            //
                            reply.writeLong(WRITE_COMPLETE);
                        } finally {
                            reply.close();
                        }
                    } else if (op == OP_READ_BLOCK || op == OP_READSKIP_BLOCK) {
                        //
                        // Read in the header
                        //
                        Block b = new Block();
                        b.readFields(in);

                        long toSkip = 0;
                        if (op == OP_READSKIP_BLOCK) {
                            toSkip = in.readLong();
                        }

                        //
                        // Open reply stream
                        //
                        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
                        try {
                            //
                            // Write filelen of -1 if error
                            //
                            if (! data.isValidBlock(b)) {
                                out.writeLong(-1);
                            } else {
                                //
                                // Get blockdata from disk
                                //
                                long len = data.getLength(b);
                                DataInputStream in2 = new DataInputStream(data.getBlockData(b));
                                out.writeLong(len);

                                if (op == OP_READSKIP_BLOCK) {
                                    if (toSkip > len) {
                                        toSkip = len;
                                    }
                                    long amtSkipped = in2.skip(toSkip);
                                    out.writeLong(amtSkipped);
                                }

                                byte buf[] = new byte[4096];
                                try {
                                    int bytesRead = in2.read(buf);
                                    while (bytesRead >= 0) {
                                        out.write(buf, 0, bytesRead);
                                        len -= bytesRead;
                                        bytesRead = in2.read(buf);
                                    }
                                } catch (SocketException se) {
                                    // This might be because the reader
                                    // closed the stream early
                                } finally {
                                    in2.close();
                                }
                            }
                            LOG.info("Served block " + b + " to " + s.getInetAddress());
                        } finally {
                            out.close();
                        }
                    } else {
                        while (op >= 0) {
                            System.out.println("Faulty op: " + op);
                            op = (byte) in.read();
                        }
                        throw new IOException("Unknown opcode for incoming data stream");
                    }
                } finally {
                    in.close();
                }
            } catch (IOException ie) {
                ie.printStackTrace();
            } finally {
                try {
                    s.close();
                } catch (IOException ie2) {
                }
            }
        }
    }

    /**
     * Used for transferring a block of data
     */
    class DataTransfer implements Runnable {
        InetSocketAddress curTarget;
        DatanodeInfo targets[];
        Block b;
        byte buf[];

        /**
         * Connect to the first item in the target list.  Pass along the 
         * entire target list, the block, and the data.
         */
        public DataTransfer(DatanodeInfo targets[], Block b) throws IOException {
            this.curTarget = createSocketAddr(targets[0].getName().toString());
            this.targets = targets;
            this.b = b;
            this.buf = new byte[2048];
        }

        /**
         * Do the deed, write the bytes
         */
        public void run() {
            try {
                Socket s = new Socket(curTarget.getAddress(), curTarget.getPort());
                DataOutputStream out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
                try {
                    long filelen = data.getLength(b);
                    DataInputStream in = new DataInputStream(new BufferedInputStream(data.getBlockData(b)));
                    try {
                        //
                        // Header info
                        //
                        out.write(OP_WRITE_BLOCK);
                        b.write(out);
                        out.writeInt(targets.length);
                        for (int i = 0; i < targets.length; i++) {
                            targets[i].write(out);
                        }
                        out.write(RUNLENGTH_ENCODING);
                        out.writeLong(filelen);

                        //
                        // Write the data
                        //
                        while (filelen > 0) {
                            int bytesRead = in.read(buf, 0, (int) Math.min(filelen, buf.length));
                            out.write(buf, 0, bytesRead);
                            filelen -= bytesRead;
                        }
                    } finally {
                        in.close();
                    }
                } finally {
                    out.close();
                }
                LOG.info("Replicated block " + b + " to " + curTarget);
            } catch (IOException ie) {
            }
        }
    }

    /**
     */
    public static void main(String argv[]) throws IOException {
        String dataDir = NutchConf.get().get("ndfs.data.dir",
                                             "/tmp/nutch/data/name");
        if (argv.length > 0){
            dataDir=argv[0];
        } 
        LOG.info("Using ["+dataDir+"] directory for data storage.");
            
        DataNode datanode = new DataNode(dataDir);
        while (true) {
            try {
                datanode.offerService();
            } catch (Exception ex) {
                LOG.info("Lost connection to namenode [" + datanode.getNamenode() + "].  Retrying...");
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                }
            }
        }
    }
}
