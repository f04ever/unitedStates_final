package International_Trade_Union.controllers;

import International_Trade_Union.entity.*;
import International_Trade_Union.entity.blockchain.DataShortBlockchainInformation;
import org.json.JSONException;

import org.springframework.http.MediaType;
import International_Trade_Union.entity.DtoTransaction.DtoTransaction;
import International_Trade_Union.entity.blockchain.Blockchain;
import International_Trade_Union.entity.blockchain.block.Block;
import International_Trade_Union.config.BLockchainFactory;
import International_Trade_Union.config.BlockchainFactoryEnum;
import International_Trade_Union.model.Account;
import International_Trade_Union.model.Mining;
import International_Trade_Union.model.User;
import International_Trade_Union.network.AllTransactions;
import International_Trade_Union.setings.Seting;
import International_Trade_Union.utils.*;
import International_Trade_Union.vote.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.security.*;
import java.security.spec.InvalidKeySpecException;

import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

import static International_Trade_Union.utils.UtilsBalance.calculateBalance;

@Controller
public class BasisController {
    private static Account minerShow = null;
    private static boolean mining = false;
    private static boolean updating = false;
    private static DataShortBlockchainInformation shortDataBlockchain = null;
    private static Blockchain tempBlockchain;
    private static Blockchain blockchain;
    private static int blockchainSize = 0;
    private static boolean blockchainValid = false;
    private static Set<String> excludedAddresses = new HashSet<>();

    public static int getBlockchainSize() {
        return blockchainSize;
    }

    /**informs if mining is happening now. информирует, происходит ли сейчас майнинг.*/
    public static boolean isMining() {
        return mining;
    }
    /**Informs whether the files are currently being updated. информирует, происходит ли сейчас обновление файлов.*/
    public static boolean isUpdating() {
        return updating;
    }

    public static HttpServletRequest getCurrentRequest() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null)
            return null;
        Assert.state(requestAttributes != null, "Could not find current request via RequestContextHolder");
        Assert.isInstanceOf(ServletRequestAttributes.class, requestAttributes);
        HttpServletRequest servletRequest = ((ServletRequestAttributes) requestAttributes).getRequest();

        Assert.state(servletRequest != null, "Could not find current HttpServletRequest");
        return servletRequest;
    }

    public static Set<String> getExcludedAddresses() {

        HttpServletRequest request = getCurrentRequest();
        if (request == null)
            return excludedAddresses;

        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();
        String contextPath = request.getContextPath();  // includes leading forward slash

        String localaddress = scheme + "://" + serverName + ":" + serverPort;

        excludedAddresses.add(localaddress);
        return excludedAddresses;
    }

    public static void setExcludedAddresses(Set<String> excludedAddresses) {
        BasisController.excludedAddresses = excludedAddresses;
    }

    private static Set<String> nodes = new HashSet<>();
//    private static Nodes nodes = new Nodes();

    public static void setNodes(Set<String> nodes) {
        BasisController.nodes = nodes;
    }

    /**
     * returns a list of hosts to which you can connect automatically.
     *  возвращает список хостов которому можно подключиться автоматически.
     */
    public static Set<String> getNodes() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, SignatureException, NoSuchProviderException, InvalidKeyException {

        nodes = new HashSet<>();

        Set<String> temporary = UtilsAllAddresses.readLineObject(Seting.ORIGINAL_POOL_URL_ADDRESS_FILE);


        nodes.addAll(temporary);


        nodes = nodes.stream()
                .filter(t -> !t.isBlank())
                .filter(t -> t.startsWith("\""))
                .collect(Collectors.toSet());
        nodes = nodes.stream().map(t -> t.replaceAll("\"", "")).collect(Collectors.toSet());
        Set<String> bloked = UtilsAllAddresses.readLineObject(Seting.ORIGINAL_POOL_URL_ADDRESS_BLOCKED_FILE);
        //removes blocked hosts. удаляет заблокированные хосты
        nodes.removeAll(bloked);
        nodes.removeAll(Seting.ORIGINAL_BLOCKED_ADDRESS);

        //adds preset hosts. добавляет предустановные хосты
        nodes.addAll(Seting.ORIGINAL_ADDRESSES);
        return nodes;
    }

    /**similar to getNodes. аналогичен getNodes*/
    @GetMapping("/getNodes")
    public Set<String> getAllNodes() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, SignatureException, NoSuchProviderException, InvalidKeyException {
        Set<String> temporary = UtilsAllAddresses.readLineObject(Seting.ORIGINAL_POOL_URL_ADDRESS_FILE);
        nodes.addAll(temporary);
        nodes.addAll(Seting.ORIGINAL_ADDRESSES);
        nodes = nodes.stream().filter(t -> t.startsWith("\""))
                .collect(Collectors.toSet());
        Set<String> bloked = UtilsAllAddresses.readLineObject(Seting.ORIGINAL_POOL_URL_ADDRESS_BLOCKED_FILE);
        nodes.removeAll(bloked);
        nodes.removeAll(Seting.ORIGINAL_BLOCKED_ADDRESS);
        return nodes;
    }

    /**
     * Возвращает действующий блокчейн. Returns a valid blockchain
     */
    public static Blockchain getBlockchain() {
        return blockchain;
    }

    public static synchronized void setBlockchain(Blockchain blockchain) {
        BasisController.blockchain = blockchain;
    }

    static {
        try {
            //creates all resource folders to work with
            //создает все папки ресурсов для работы
            UtilsCreatedDirectory.createPackages();

            //downloads from a blockchain file
            //загрузки из файла блокчейна
            blockchain = BLockchainFactory.getBlockchain(BlockchainFactoryEnum.ORIGINAL);
            blockchain = Mining.getBlockchain(
                    Seting.ORIGINAL_BLOCKCHAIN_FILE,
                    BlockchainFactoryEnum.ORIGINAL);

            //a shorthand object that stores information about the blockchain
            //сокращенный объект, который хранит информацию о блокчейне
            shortDataBlockchain = Blockchain.checkFromFile(Seting.ORIGINAL_BLOCKCHAIN_FILE);
            blockchainSize = (int) shortDataBlockchain.getSize();
            blockchainValid = shortDataBlockchain.isValidation();


        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (SignatureException e) {
            throw new RuntimeException(e);
        } catch (NoSuchProviderException e) {
            throw new RuntimeException(e);
        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    public BasisController() {
    }

    //TODO если вы прервали mine, то перед следующим вызовом перезапустите сервер и вызовите /addBlock перед mine
    //TODO if you interrupted mine, restart the server before next call and call /addBlock before mine
    //TODO иначе будет расождение в файле балансов
    //TODO otherwise there will be a discrepancy in the balance file

    /**
     * Returns an EntityChain which stores the size of the blockchain and the list of blocks
     * Возвращает EntityChain который, хранит в себе размер блокчейна и список блоков
     */
    @GetMapping("/chain")
    @ResponseBody
    public EntityChain full_chain() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, SignatureException, NoSuchProviderException, InvalidKeyException {
        if (blockchainValid == false || blockchainSize == 0) {
            blockchain = Mining.getBlockchain(
                    Seting.ORIGINAL_BLOCKCHAIN_FILE,
                    BlockchainFactoryEnum.ORIGINAL);
            shortDataBlockchain = Blockchain.checkFromFile(Seting.ORIGINAL_BLOCKCHAIN_FILE);
            blockchainSize = (int) shortDataBlockchain.getSize();
            blockchainValid = shortDataBlockchain.isValidation();
        }

        return new EntityChain(blockchainSize, blockchain.getBlockchainList());
    }

    /**
     * returns the size of the local blockchain
     * возвращает размер локального блокчейна
     */
    @GetMapping("/size")
    @ResponseBody
    public Integer sizeBlockchain() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, SignatureException, NoSuchProviderException, InvalidKeyException {

        blockchain = Mining.getBlockchain(
                Seting.ORIGINAL_BLOCKCHAIN_FILE,
                BlockchainFactoryEnum.ORIGINAL);
        shortDataBlockchain = Blockchain.checkFromFile(Seting.ORIGINAL_BLOCKCHAIN_FILE);
        blockchainSize = (int) shortDataBlockchain.getSize();
        blockchainValid = shortDataBlockchain.isValidation();
        System.out.println(":sizeBlockchain: " + shortDataBlockchain);

        return blockchainSize;
    }

    /**
     * Returns a list of blocks from to to,
     * Возвращает список блоков ОТ до ДО,
     */
    @PostMapping("/sub-blocks")
    @ResponseBody
    public List<Block> subBlocks(@RequestBody SubBlockchainEntity entity) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, SignatureException, NoSuchProviderException, InvalidKeyException {
        if (blockchainValid == false || blockchainSize == 0) {
            blockchain = Mining.getBlockchain(
                    Seting.ORIGINAL_BLOCKCHAIN_FILE,
                    BlockchainFactoryEnum.ORIGINAL);
        }

        return Blockchain.subFromFile(entity.getStart(), entity.getFinish(), Seting.ORIGINAL_BLOCKCHAIN_FILE);
    }

    /**
     * Returns a block by index
     * Возвращает блок по индексу
     */
    @PostMapping("/block")
    @ResponseBody
    public Block getBlock(@RequestBody Integer index) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, SignatureException, NoSuchProviderException, InvalidKeyException {
        if (blockchainValid == false || blockchainSize == 0) {
            blockchain = Mining.getBlockchain(
                    Seting.ORIGINAL_BLOCKCHAIN_FILE,
                    BlockchainFactoryEnum.ORIGINAL);
        }

//        return blockchain.getBlock(index);
        return Blockchain.indexFromFile(index, Seting.ORIGINAL_BLOCKCHAIN_FILE);
    }

    /**
     * connects to the storage and updates its internal blockchain.
     * подключается к хранилищу и обновляет свой внутренний блокчейн*/
    @GetMapping("/nodes/resolve")
    public synchronized int resolve_conflicts() throws NoSuchAlgorithmException, InvalidKeySpecException, IOException, SignatureException, NoSuchProviderException, InvalidKeyException, JSONException {
        System.out.println(":resolve_conflicts");
        return resolve();
    }

    //TODO need to improve the code, at the moment even if you download one block,
    //TODO have to recalculate the entire blockchain again to get the current balance
    //TODO нужно улучшить код, на данный момент даже если вы скачиваете один блок,
    //TODO приходиться пересчитывать весь блокчейн заново, чтобы получить актуальный баланс
    /**
     * Updates the blockchain. Connects to the storage host and downloads if the host has a more up-to-date blockchain.
     * Обновляет блокчейн. Подключается к хосту хранилища и скачивает, если в на хосте более актуальный блокчейн.
*/
    public static int resolve() throws NoSuchAlgorithmException, InvalidKeySpecException, IOException, SignatureException, NoSuchProviderException, InvalidKeyException {
        updating = true;
        boolean isPortion = false;
        boolean isBigPortion = false;
        try {
            System.out.println(" :start resolve");
            Blockchain temporaryBlockchain = BLockchainFactory.getBlockchain(BlockchainFactoryEnum.ORIGINAL);
            Blockchain bigBlockchain = BLockchainFactory.getBlockchain(BlockchainFactoryEnum.ORIGINAL);
            if (blockchainValid == false || blockchainSize == 0) {
                blockchain = Mining.getBlockchain(
                        Seting.ORIGINAL_BLOCKCHAIN_FILE,
                        BlockchainFactoryEnum.ORIGINAL);
                shortDataBlockchain = Blockchain.checkFromFile(Seting.ORIGINAL_BLOCKCHAIN_FILE);
                blockchainSize = (int) shortDataBlockchain.getSize();
                blockchainValid = shortDataBlockchain.isValidation();
            }

            //size of the most recent long blockchain downloaded from hosts (storage)
            //размер самого актуального длинного блокчейна, скачанного из хостов (хранилище)
            int bigSize = 0;

            //local blockchain size
            //размер локального блокчейна
            int blocks_current_size = blockchainSize;

            //the sum of the complexity (all zeros) of the temporary blockchain, needed to select the most complex blockchain
            //сумма сложности (всех нулей) временного блокчейна, нужна чтобы отобрать самый сложный блокчейн
            long hashCountZeroTemporary = 0;

            //the sum of the complexity (all zeros) of the longest downloaded blockchain is needed to select the most complex blockchain
            //сумма сложности (всех нулей) самого длинного блокчейна из скачанных, нужна чтобы отобрать самый сложный блокчейн
            long hashCountZeroBigBlockchain = 0;

            EntityChain entityChain = null;
            System.out.println(" :resolve_conflicts: blocks_current_size: " + blocks_current_size);

            //the sum of the complexity (all zeros) of the local blockchain
            //сумма сложности (всех нулей) локального блокчейна
            long hashCountZeroAll = 0;

            //get the total complexity of the local blockchain
            //получить общую сложность локального блокчейна
            hashCountZeroAll = shortDataBlockchain.getHashCount();

            Set<String> nodesAll = getNodes();

            System.out.println(":BasisController: resolve_conflicts: size nodes: " + getNodes().size());

            //goes through all hosts (repositories) in search of the most up-to-date blockchain
            //проходит по всем хостам(хранилищам) в поисках самого актуального блокчейна
            for (String s : nodesAll) {
                System.out.println(":while resolve_conflicts: node address: " + s);
                String temporaryjson = null;

                //if the local address matches the host address, it skips
                //если локальный адрес совпадает с адресом хоста, он пропускает
                if (BasisController.getExcludedAddresses().contains(s)) {
                    System.out.println(":its your address or excluded address: " + s);
                    continue;
                }
                try {
                    //if the address is localhost, it skips
                    //если адрес локального хоста, он пропускает
                    if (s.contains("localhost") || s.contains("127.0.0.1"))
                        continue;



                    System.out.println("start:BasisController:resolve conflicts: address: " + s + "/size");

                    String sizeStr = UtilUrl.readJsonFromUrl(s + "/size");
                    Integer size = Integer.valueOf(sizeStr);
//                    MainController.setGlobalSize(size);
                    System.out.println(" :resolve_conflicts: finish /size: " + size);
                    //if the size from the storage is larger than on the local server, start checking
                    //если размер с хранилища больше чем на локальном сервере, начать проверку
                    if (size > blocks_current_size) {

                        System.out.println(":size from address: " + s + " upper than: " + size + ":blocks_current_size " + blocks_current_size);
                        //Test start algorithm
                        List<Block> emptyList = new ArrayList<>();
                        SubBlockchainEntity subBlockchainEntity = null;
                        String subBlockchainJson = null;

                        //if the local one lags behind the global one by more than PORTION_DOWNLOAD, then you need to download in portions from the storage
                        //если локальный отстает от глобального больше чем PORTION_DOWNLOAD, то нужно скачивать порциями из хранилища
                        if (size - blocks_current_size > Seting.PORTION_DOWNLOAD) {
                            boolean downloadPortion = true;
                            int finish = blocks_current_size + Seting.PORTION_DOWNLOAD;
                            int start = blocks_current_size;
                            //while the difference in the size of the local blockchain is greater than from the host, it will continue to download in portions to download the entire blockchain
                            //пока разница размера локального блокчейна больше чем с хоста будет продожаться скачивать порциями, чтобы скачать весь блокчейн
                            while (downloadPortion) {

                                subBlockchainEntity = new SubBlockchainEntity(start, finish);

                                System.out.println("downloadPortion: " + subBlockchainEntity.getStart() +
                                        ": " + subBlockchainEntity.getFinish());
                                subBlockchainJson = UtilsJson.objToStringJson(subBlockchainEntity);

                                List<Block> subBlocks = UtilsJson.jsonToListBLock(UtilUrl.getObject(subBlockchainJson, s + "/sub-blocks"));
                                finish = (int) subBlocks.get(subBlocks.size() - 1).getIndex() + Seting.PORTION_DOWNLOAD;
                                start = (int) subBlocks.get(subBlocks.size() - 1).getIndex() + 1;

                                emptyList.addAll(subBlocks);
                                System.out.println("subblocks: " + subBlocks.get(0).getIndex() + ":"
                                        + subBlocks.get(subBlocks.size() - 1).getIndex());

                                if (size - emptyList.get(emptyList.size() - 1).getIndex() < Seting.PORTION_DOWNLOAD) {
                                    downloadPortion = false;
                                    finish = size;
                                    subBlockchainEntity = new SubBlockchainEntity(start, finish);
                                    subBlockchainJson = UtilsJson.objToStringJson(subBlockchainEntity);
                                    subBlocks = UtilsJson.jsonToListBLock(UtilUrl.getObject(subBlockchainJson, s + "/sub-blocks"));
                                    System.out.println("subblocks: " + subBlocks.get(0).getIndex() + ":"
                                            + subBlocks.get(subBlocks.size() - 1).getIndex());
                                    emptyList.addAll(subBlocks);
                                }
                            }
                        } else {
                            //If the difference is not greater than PORTION_DOWNLOAD, then downloads once a portion of this difference
                            //Если разница не больше PORTION_DOWNLOAD, то скачивает один раз порцию эту разницу
                            subBlockchainEntity = new SubBlockchainEntity(blocks_current_size, size);
                            subBlockchainJson = UtilsJson.objToStringJson(subBlockchainEntity);

                            System.out.println(":download sub block: " + subBlockchainJson);

                            List<Block> subBlocks = UtilsJson.jsonToListBLock(UtilUrl.getObject(subBlockchainJson, s + "/sub-blocks"));
                            emptyList.addAll(subBlocks);

                            System.out.println("subblocks: " + subBlocks.get(0).getIndex() + ":"
                                    + subBlocks.get(subBlocks.size() - 1).getIndex());
                            System.out.println("blocks_current_size: " + blocks_current_size);
                            System.out.println("sub: " + subBlocks.get(0).getIndex() + ":" + subBlocks.get(0).getHashBlock() + ":"
                                    + "prevHash: " + subBlocks.get(0).getPreviousHash());
                        }

                        //if the local blockchain was originally greater than 0, then add part of the missing list of blocks to the list.
                        //если локальный блокчейн изначально был больше 0, то добавить в список часть недостающего списка блоков.
                        if (blocks_current_size > 0) {
                            System.out.println("sub: from 0 " + ":" + blocks_current_size);
                            List<Block> temp = blockchain.subBlock(0, blocks_current_size);

                            emptyList.addAll(temp);
                        }


                        emptyList = emptyList.stream().sorted(Comparator.comparing(Block::getIndex)).collect(Collectors.toList());
                        temporaryBlockchain.setBlockchainList(emptyList);


                        System.out.println("size temporaryBlockchain: " + temporaryBlockchain.sizeBlockhain());
                        System.out.println("resolve: temporaryBlockchain: " + temporaryBlockchain.validatedBlockchain());

                        //if the global blockchain is larger but there is a branching in the blockchain, for example, the global size is 25,
                        // the local size is 20,
                        //but from block 15 they differ, then you need to remove all blocks from the local block from block 15
                        // and add 15-25 blocks from the global blockchain there
                        //если глобальный блокчейн больше но есть развлетление в блокчейне, к примеру глобальный размер 25,
                        // локальный 20,
                        //но с 15 блока они отличаются, то нужно удалить из локального с
                        // 15 все блоки и добавить туда 15-25 с глобального блокчейна

                        if(temporaryBlockchain.validatedBlockchain() && blockchainSize > 1){
                            isPortion = true;
                        }else {
                            isPortion = false;
                        }
                        if (!temporaryBlockchain.validatedBlockchain()) {
                            System.out.println(":download blocks");
                            emptyList = new ArrayList<>();

                            for (int i = size - 1; i > 0; i--) {

                                Block block = UtilsJson.jsonToBLock(UtilUrl.getObject(UtilsJson.objToStringJson(i), s + "/block"));

                                System.out.println("block index: " + block.getIndex());
                                if (i > blocks_current_size - 1) {
                                    System.out.println(":download blocks: " + block.getIndex() +
                                            " your block : " + (blocks_current_size) + ":wating need downoad blocks: " + (block.getIndex() - blocks_current_size));
                                    emptyList.add(block);
                                } else if (!blockchain.getBlock(i).getHashBlock().equals(block.getHashBlock())) {
                                    emptyList.add(block);
                                    System.out.println("********************************");
                                    System.out.println(":dowdnload block index: " + i);
                                    System.out.println(":block original index: " + blockchain.getBlock(i).getIndex());
                                    System.out.println(":block from index: " + block.getIndex());
                                    System.out.println("---------------------------------");
                                } else {
                                    emptyList.add(block);

                                    if (i != 0) {
                                        System.out.println("portion:sub: " + 0 + " : " + i + " block index: " + block.getIndex());
                                        emptyList.addAll(blockchain.subBlock(0, i));
                                    }

                                    emptyList = emptyList.stream().sorted(Comparator.comparing(Block::getIndex)).collect(Collectors.toList());
                                    temporaryBlockchain.setBlockchainList(emptyList);
                                    System.out.println("<><><<><><><>><><><><><><><<>><><><><>");
                                    System.out.println(":resolve_conflicts: temporaryBlockchain: " + temporaryBlockchain.validatedBlockchain());
                                    System.out.println(":dowdnload block index: " + i);
                                    System.out.println(":block original index: " + blockchain.getBlock(i).getIndex());
                                    System.out.println(":block from index: " + block.getIndex());
                                    System.out.println("<><><<><><><>><><><><><><><<>><><><><>");
                                    break;
                                }
                            }
                        }
                    } else {
                        System.out.println(":BasisController: resove: size less: " + size + " address: " + s);
                        continue;
                    }
                } catch (IOException e) {

                    System.out.println(":BasisController: resolve_conflicts: connect refused Error: " + s);
                    continue;
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                } catch (CloneNotSupportedException e) {
                    throw new RuntimeException(e);
                }

                //if the global blockchain is correct and it is larger than the longest previous temporary blockchain, then make it a contender as a future local blockchain
                //если глобальный блокчейн верный и он больше самого длиного предыдущего временного блокчейна, то сделать его претендентом в качестве будущего локального блокчейна
                if (temporaryBlockchain.validatedBlockchain()) {
                    if (bigSize < temporaryBlockchain.sizeBlockhain()) {
                        isBigPortion = isPortion;
                        bigSize = temporaryBlockchain.sizeBlockhain();
                    }
                    for (Block block : temporaryBlockchain.getBlockchainList()) {
                        hashCountZeroTemporary += UtilsUse.hashCount(block.getHashBlock());
                    }

                    if (blocks_current_size < temporaryBlockchain.sizeBlockhain() && hashCountZeroAll < hashCountZeroTemporary) {
                        blocks_current_size = temporaryBlockchain.sizeBlockhain();
                        bigBlockchain = temporaryBlockchain;
                        hashCountZeroBigBlockchain = hashCountZeroTemporary;
                    }
                    hashCountZeroTemporary = 0;
                }

            }

            System.out.println("bigBlockchain: " + bigBlockchain.validatedBlockchain() + " : " + bigBlockchain.sizeBlockhain());
            //Only the blockchain that is not only the longest but also the most complex will be accepted.
            //Будет принять только тот блокчейн который является не только самым длинным, но и самым сложным.
            if (bigBlockchain.validatedBlockchain() && bigBlockchain.sizeBlockhain() > blockchainSize && hashCountZeroBigBlockchain > hashCountZeroAll) {
                System.out.println("resolve start addBlock start: ");
                blockchain = bigBlockchain;
                if(isBigPortion){
                    List<Block> temp = bigBlockchain.subBlock(blockchainSize, bigBlockchain.sizeBlockhain());
                    Map<String, Account> balances = SaveBalances.readLineObject(Seting.ORIGINAL_BALANCE_FILE);
                    addBlock2(temp,
                           balances );
                    System.out.println("temp size: " + temp.size());

                }else {


                    UtilsBlock.deleteFiles();
                    addBlock(bigBlockchain.getBlockchainList());
                }
                List<Block> temp = bigBlockchain.subBlock(blockchainSize, bigBlockchain.sizeBlockhain());

                System.out.println("size: " + blockchainSize);
                System.out.println(":BasisController: resolve: bigblockchain size: " + bigBlockchain.sizeBlockhain());
                System.out.println(":BasisController: resolve: validation bigblochain: " + bigBlockchain.validatedBlockchain());

                System.out.println("isPortion: " + isPortion + ":isBigPortion: " +  isBigPortion + " size: " + temp.size());
                if (blockchainSize > bigSize) {
                    return 1;
                } else if (blockchainSize < bigSize) {
                    return -1;
                } else {
                    return 0;
                }
            }
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        } finally {
            updating = false;
        }
        return -4;

    }

    /**rewrites the blockchain to files
     * производит перезапись блокчейна в файлы*/

    public static void addBlock2(List<Block> originalBlocks, Map<String, Account> balances) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, SignatureException, NoSuchProviderException, InvalidKeyException {

        //delete all files from resources folder
        //удалить все файлы из папки resources

        System.out.println(" addBlock2 start: ");

        //write a new blockchain from scratch to the resources folder
        //записать с нуля новый блокчейн в папку resources
        for (Block block : originalBlocks) {
            System.out.println(" :BasisController: addBlock2: blockchain is being updated: ");
            UtilsBlock.saveBLock(block, Seting.ORIGINAL_BLOCKCHAIN_FILE);
        }

        blockchain = Mining.getBlockchain(
                Seting.ORIGINAL_BLOCKCHAIN_FILE,
                BlockchainFactoryEnum.ORIGINAL);
        shortDataBlockchain = Blockchain.checkFromFile(Seting.ORIGINAL_BLOCKCHAIN_FILE);
        blockchainSize = (int) shortDataBlockchain.getSize();
        blockchainValid = shortDataBlockchain.isValidation();

        //recalculation of the balance
        //перерасчет баланса
        List<String> signs = new ArrayList<>();
        Map<String, Laws> allLaws = new HashMap<>();
        List<LawEligibleForParliamentaryApproval> allLawsWithBalance = new ArrayList<>();
        for (Block block :  originalBlocks) {
            calculateBalance(balances, block, signs);
            balances = UtilsBalance.calculateBalanceFromLaw(balances, block, allLaws, allLawsWithBalance);
        }

        Mining.deleteFiles(Seting.ORIGINAL_BALANCE_FILE);
        SaveBalances.saveBalances(balances, Seting.ORIGINAL_BALANCE_FILE);

        //removal of obsolete laws
        //удаление устаревших законов
//        Mining.deleteFiles(Seting.ORIGINAL_ALL_CORPORATION_LAWS_WITH_BALANCE_FILE);

        //rewriting all existing laws
        //перезапись всех действующих законов
        UtilsLaws.saveCurrentsLaws(allLawsWithBalance, Seting.ORIGINAL_ALL_CORPORATION_LAWS_WITH_BALANCE_FILE);

        System.out.println(":BasisController: addBlock2: finish: " + originalBlocks.size());
    }
    public static void addBlock(List<Block> orignalBlocks) throws IOException, NoSuchAlgorithmException, SignatureException, InvalidKeySpecException, NoSuchProviderException, InvalidKeyException {

        Map<String, Account> balances = new HashMap<>();
        Blockchain temporaryForValidation = BLockchainFactory.getBlockchain(BlockchainFactoryEnum.ORIGINAL);
        temporaryForValidation.setBlockchainList(orignalBlocks);
        //delete all files from resources folder
        //удалить все файлы из папки resources
        UtilsBlock.deleteFiles();
        System.out.println(" addBlock start: ");

        //write a new blockchain from scratch to the resources folder
        //записать с нуля новый блокчейн в папку resources
        for (Block block : orignalBlocks) {
            System.out.println(" :BasisController: addBlock: blockchain is being updated: ");
            UtilsBlock.saveBLock(block, Seting.ORIGINAL_BLOCKCHAIN_FILE);
        }

        blockchain = Mining.getBlockchain(
                Seting.ORIGINAL_BLOCKCHAIN_FILE,
                BlockchainFactoryEnum.ORIGINAL);
        shortDataBlockchain = Blockchain.checkFromFile(Seting.ORIGINAL_BLOCKCHAIN_FILE);
        blockchainSize = (int) shortDataBlockchain.getSize();
        blockchainValid = shortDataBlockchain.isValidation();

        //recalculation of the balance
        //перерасчет баланса
        balances = UtilsBalance.calculateBalances(blockchain.getBlockchainList());
        Mining.deleteFiles(Seting.ORIGINAL_BALANCE_FILE);
        SaveBalances.saveBalances(balances, Seting.ORIGINAL_BALANCE_FILE);


        //получение и отображение законов, а также сохранение новых законов
        //и изменение действующих законов
        Map<String, Laws> allLaws = UtilsLaws.getLaws(blockchain.getBlockchainList(), Seting.ORIGINAL_ALL_CORPORATION_LAWS_FILE);

        //возвращает все законы с балансом
        List<LawEligibleForParliamentaryApproval> allLawsWithBalance = UtilsLaws.getCurrentLaws(allLaws, balances,
                Seting.ORIGINAL_ALL_CORPORATION_LAWS_WITH_BALANCE_FILE);

        //removal of obsolete laws
        //удаление устаревших законов
        Mining.deleteFiles(Seting.ORIGINAL_ALL_CORPORATION_LAWS_WITH_BALANCE_FILE);
        //rewriting all existing laws
        //перезапись всех действующих законов
        UtilsLaws.saveCurrentsLaws(allLawsWithBalance, Seting.ORIGINAL_ALL_CORPORATION_LAWS_WITH_BALANCE_FILE);

        System.out.println(":BasisController: addBlock: finish");
    }

    /**overwrites the current blockchain in the resources folder.
     * производит перезапись текущего блокчейна в папку ресурсы*/
    @GetMapping("/addBlock")
    public ResponseEntity getBLock() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, SignatureException, NoSuchProviderException, InvalidKeyException {

        blockchain = Mining.getBlockchain(
                Seting.ORIGINAL_BLOCKCHAIN_FILE,
                BlockchainFactoryEnum.ORIGINAL);
        UtilsBlock.deleteFiles();
        addBlock(blockchain.getBlockchainList());
        shortDataBlockchain = Blockchain.checkFromFile(Seting.ORIGINAL_BLOCKCHAIN_FILE);
        blockchainSize = (int) shortDataBlockchain.getSize();
        blockchainValid = shortDataBlockchain.isValidation();
        return new ResponseEntity(HttpStatus.OK);
    }


    /**get a block. добыть блок*/
    @GetMapping("/miningblock")
    public synchronized ResponseEntity minings() throws JSONException, IOException, NoSuchAlgorithmException, InvalidKeySpecException, SignatureException, NoSuchProviderException, InvalidKeyException, CloneNotSupportedException {
        mining();
        return new ResponseEntity("OK", HttpStatus.OK);
    }


    /**get a block. добыть блок*/
    @GetMapping("/process-mining")
    public synchronized String proccessMining(Model model, Integer number) throws JSONException, IOException, NoSuchAlgorithmException, InvalidKeySpecException, SignatureException, NoSuchProviderException, InvalidKeyException, CloneNotSupportedException {
        mining();
        model.addAttribute("title", "mining proccess");
        return "redirect:/process-mining";
    }


    /**blockchain update. обновление блокчейна*/
    @RequestMapping("/resolving")
    public String resolving() throws JSONException, NoSuchAlgorithmException, InvalidKeySpecException, IOException, SignatureException, NoSuchProviderException, InvalidKeyException {
        resolve_conflicts();
        return "redirect:/";
    }


    /**sends the mined block to the storage server.  отправляет добытый блок на сервер хранилища*/
    @RequestMapping("/sendBlocks")
    public String sending() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, SignatureException, NoSuchProviderException, InvalidKeyException {
        if (blockchainValid == false || blockchainSize == 0) {
            blockchain = Mining.getBlockchain(
                    Seting.ORIGINAL_BLOCKCHAIN_FILE,
                    BlockchainFactoryEnum.ORIGINAL);
            shortDataBlockchain = Blockchain.checkFromFile(Seting.ORIGINAL_BLOCKCHAIN_FILE);
            blockchainSize = (int) shortDataBlockchain.getSize();
            blockchainValid = shortDataBlockchain.isValidation();
        }
        System.out.println("sendBlocks: size: " + blockchain.sizeBlockhain());
        sendAllBlocksToStorage(blockchain.getBlockchainList());
        return "redirect:/";
    }

    /**
     *Registers a new external host. Регистрирует новый внешний хост
     */
    @RequestMapping(method = RequestMethod.POST, value = "/nodes/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    public synchronized void register_node(@RequestBody AddressUrl urlAddrress) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, SignatureException, NoSuchProviderException, InvalidKeyException {


        for (String s : BasisController.getNodes()) {
            String original = s;
            String url = s + "/nodes/register";

//            try {
//                UtilUrl.sendPost(urlAddrress.getAddress(), url);
//                sendAddress();
//
//
//            } catch (Exception e) {
//                System.out.println(":BasisController: register node: wrong node: " + original);
//                BasisController.getNodes().remove(original);
//                continue;
//            }
        }

        Set<String> nodes = BasisController.getNodes();
        nodes = nodes.stream()
                .map(t -> t.replaceAll("\"", ""))
                .map(t -> t.replaceAll("\\\\", ""))
                .collect(Collectors.toSet());
        nodes.add(urlAddrress.getAddress());
        BasisController.setNodes(nodes);

        Mining.deleteFiles(Seting.ORIGINAL_POOL_URL_ADDRESS_FILE);
        nodes.stream().forEach(t -> {
            try {
                UtilsAllAddresses.saveAllAddresses(t, Seting.ORIGINAL_POOL_URL_ADDRESS_FILE);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            } catch (SignatureException e) {
                throw new RuntimeException(e);
            } catch (InvalidKeySpecException e) {
                throw new RuntimeException(e);
            } catch (NoSuchProviderException e) {
                throw new RuntimeException(e);
            } catch (InvalidKeyException e) {
                throw new RuntimeException(e);
            }
        });

    }


    /**
     * connects to other nodes and takes their lists of hosts that are stored by them,
     *       and keeps these lists at home. (currently partially disabled).
     * подключается к другим узлам и у них берет их списки хостов, которые хранятся у них,
     *       и сохраняет эти списки у себя. (на данный момент частично отключен).
     */

    @GetMapping("/findAddresses")
    public void findAddresses() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, SignatureException, NoSuchProviderException, InvalidKeyException {
        for (String s : Seting.ORIGINAL_ADDRESSES) {
            Set<String> addressesSet = new HashSet<>();
            try {
                register_node(new AddressUrl(s));
                System.out.println(":start download addressses");
                System.out.println("trying to connect to the server:" + s + " timeout 45 seconds");
//                String addresses = UtilUrl.readJsonFromUrl(s + "/getDiscoveryAddresses");
//                addressesSet = UtilsJson.jsonToSetAddresses(addresses);
                System.out.println(":finish download addreses");
            } catch (IOException e) {
                System.out.println(":BasisController: findAddress: error");
                continue;
            }

        }

    }

    /**
     * Starts an automatic mining cycle, the cycle will go 2000 steps
     * Запускает автоматический цикл майнинга, цикл будет идти 2000 шагов
     */
    @GetMapping("/moreMining")
    public void moreMining() throws JSONException, IOException {
        for (int i = 1; i < 2000; i++) {
            System.out.println("block generate i: " + i);
            UtilUrl.readJsonFromUrl("http://localhost:8082/mine");


        }
    }


    /**
     * Sends its list of hosts to other nodes and tries to automatically register with them
     * Отправляет свой список хостов, другим узлам, и пытается автоматически регистрировать у них
     */
    public static void sendAddress() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, SignatureException, NoSuchProviderException, InvalidKeyException {
        //лист временный для отправки аддресов

        for (String s : Seting.ORIGINAL_ADDRESSES) {
            System.out.println(new Date() + ":start send addreses");
            String original = s;
            String url = s + "/nodes/register";

            if (BasisController.getExcludedAddresses().contains(url)) {
                System.out.println(":MainController: its your address or excluded address: " + url);
                continue;
            }
            try {
                for (String s1 : BasisController.getNodes()) {

                    System.out.println(":trying to connect to the server: send addreses: " + s + ": timeout 45 seconds");
                    AddressUrl addressUrl = new AddressUrl(s1);
                    String json = UtilsJson.objToStringJson(addressUrl);
                    UtilUrl.sendPost(json, url);
                }
            } catch (Exception e) {
                System.out.println(":BasisController: sendAddress: wronge node: " + original);

                continue;
            }


        }
        System.out.println(":finish send addressess");
    }


    /**
     * Sends a list of blocks to central stores (example: http://194.87.236.238:80)
     * Отправляет список блоков в центральные хранилища (пример: http://194.87.236.238:80)
     */
    public static int sendAllBlocksToStorage(List<Block> blocks) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, SignatureException, NoSuchProviderException, InvalidKeyException {

        System.out.println(new Date() + ":BasisController: sendAllBlocksToStorage: start: ");
        int bigsize = 0;
        int blocks_current_size = blocks.size();
        //отправка блокчейна на хранилище блокчейна
        System.out.println(":BasisController: sendAllBlocksToStorage: ");
        getNodes().stream().forEach(System.out::println);
        for (String s : getNodes()) {

            System.out.println(":trying to connect to the server send block: " + s + ": timeout 45 seconds");

            if (BasisController.getExcludedAddresses().contains(s)) {
                System.out.println(":its your address or excluded address: " + s);
                continue;
            }

            try {
                System.out.println(":BasisController:resolve conflicts: address: " + s + "/size");
                String sizeStr = UtilUrl.readJsonFromUrl(s + "/size");
                Integer size = 0;
                if (Integer.valueOf(sizeStr) > 0)
                    size = Integer.valueOf(sizeStr);
                System.out.println(":BasisController: send: local size: " + blocks_current_size + " global size: " + size);
                if (size > blocks.size()) {
                    System.out.println(":your local chain less");
                    return -1;
                }
                List<Block> fromToTempBlock = blocks.subList(size, blocks.size());
                SendBlocksEndInfo infoBlocks = new SendBlocksEndInfo(Seting.VERSION, fromToTempBlock);
                String jsonFromTo = UtilsJson.objToStringJson(infoBlocks);
                //if the current blockchain is larger than the storage, then
                //send current blockchain send to storage
                //если блокчейн текущей больше чем в хранилище, то
                //отправить текущий блокчейн отправить в хранилище
                if (size < blocks_current_size) {
                    if (bigsize < size) {
                        bigsize = size;
                    }
                    int response = -1;
                    //Test start algorithm
                    String originalF = s;
                    System.out.println(":send resolve_from_to_block");
                    String urlFrom = s + "/nodes/resolve_from_to_block";
                    try {
                        response = UtilUrl.sendPost(jsonFromTo, urlFrom);
                        System.out.println(":CONFLICT TREE, IN GLOBAL DIFFERENT TREE " + HttpStatus.CONFLICT.value());
                        System.out.println(":GOOD: SUCCESS  " + HttpStatus.OK.value());
                        System.out.println(":FAIL BAD BLOCKCHAIN: " + HttpStatus.EXPECTATION_FAILED.value());
                        System.out.println(":CONFLICT VERSION: " + HttpStatus.FAILED_DEPENDENCY.value());
                        System.out.println(":response: " + response);
                    } catch (Exception e) {
                        System.out.println(":exception resolve_from_to_block: " + originalF);

                    }
                    System.out.println(":CONFLICT TREE, IN GLOBAL DIFFERENT TREE: " + HttpStatus.CONFLICT.value());
                    System.out.println(":GOOD SUCCESS: " + HttpStatus.OK.value());
                    System.out.println(":FAIL BAD BLOCKHAIN: " + HttpStatus.EXPECTATION_FAILED.value());
                    System.out.println(":CONFLICT VERSION: " + HttpStatus.FAILED_DEPENDENCY.value());
                    System.out.println(":response: " + response);

                    System.out.println(":BasisController: sendAllBlocksStorage: response: " + response);

                    //there is an up-to-date branch on the global server, download it and delete the obsolete branch.
                    //на глобальном сервере есть актуальная ветка, скачать ее и удалить устревшую ветку.
                    if (response == HttpStatus.CONFLICT.value()) {
                        System.out.println(":BasisController: sendAllBlocksStorage: start deleted 50 blocks:");
                        System.out.println(":size before delete: " + blockchainSize);
                        blockchain = Mining.getBlockchain(
                                Seting.ORIGINAL_BLOCKCHAIN_FILE,
                                BlockchainFactoryEnum.ORIGINAL);
                        List<Block> temporary = blockchain.subBlock(0, blockchainSize - Seting.DELETED_PORTION);
                        UtilsBlock.deleteFiles();
                        blockchain.setBlockchainList(temporary);
                        UtilsBlock.saveBlocks(blockchain.getBlockchainList(), Seting.ORIGINAL_BLOCKCHAIN_FILE);
                        blockchain = Mining.getBlockchain(
                                Seting.ORIGINAL_BLOCKCHAIN_FILE,
                                BlockchainFactoryEnum.ORIGINAL);

                        shortDataBlockchain = Blockchain.checkFromFile(Seting.ORIGINAL_BLOCKCHAIN_FILE);
                        blockchainSize = (int) shortDataBlockchain.getSize();
                        blockchainValid = shortDataBlockchain.isValidation();

                        UtilsBlock.deleteFiles();
                        addBlock(blockchain.getBlockchainList());
                        System.out.println(":size after delete: " + blockchainSize);


                        int result = resolve();
                        System.out.println(":resolve: updated: " + result);
                    }


                }


            } catch (JSONException e) {
                e.printStackTrace();
                continue;

            } catch (IOException e) {
                e.printStackTrace();
                continue;
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }

        }
        if (blockchainSize > bigsize) {
            return 1;
        } else if (blockchainSize < bigsize) {
            return -1;
        } else if (blockchainSize == bigsize) {
            return 0;
        } else {
            return -4;
        }
    }

    /**mine every 576 blocks. добывать каждые 576 блоков*/
    @GetMapping("/constantMining")
    public String alwaysMining() throws JSONException, IOException, NoSuchAlgorithmException, InvalidKeySpecException, SignatureException, NoSuchProviderException, InvalidKeyException, CloneNotSupportedException {

        for (int i = 0; i < 576; i++) {
            try {
                mining();

            } catch (IllegalArgumentException e) {
                System.out.println("BasisisController: constantMining find error:");
                continue;
            } catch (IOException e) {
                System.out.println("BasisisController: constantMining find error: ");
                continue;
            }
        }
        return "redirect:/mining";
    }

    public synchronized String mining() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, SignatureException, NoSuchProviderException, InvalidKeyException, JSONException, CloneNotSupportedException {
        mining = true;
        try {

            tempBlockchain = Mining.getBlockchain(
                    Seting.ORIGINAL_BLOCKCHAIN_FILE,
                    BlockchainFactoryEnum.ORIGINAL);


            String sizeStr = "-1";
            try {
                sizeStr = UtilUrl.readJsonFromUrl("http://194.87.236.238:80" + "/size");
            } catch (NoRouteToHostException e) {
                System.out.println("home page you cannot connect to global server," +
                        "you can't give size global server");
                sizeStr = "-1";
            } catch (SocketException e) {
                System.out.println("home page you cannot connect to global server," +
                        "you can't give size global server");
                sizeStr = "-1";
            }
            Integer sizeG = Integer.valueOf(sizeStr);
            String text = "";
            //нахождение адрессов
            Map<String, Account> balances = SaveBalances.readLineObject(Seting.ORIGINAL_BALANCE_FILE);
            Account miner = balances.get(User.getUserAddress());
            minerShow = miner;
            findAddresses();

            resolve_conflicts();


            if (blockchainSize % (576 * 2) == 0) {
                System.out.println("clear storage transaction because is old");
                AllTransactions.clearAllTransaction();
            }
            //собирает класс список балансов из файла расположенного по пути Seting.ORIGINAL_BALANCE_FILE
            balances = SaveBalances.readLineObject(Seting.ORIGINAL_BALANCE_FILE);
            //собирает объект блокчейн из файла

            blockchain = Mining.getBlockchain(
                    Seting.ORIGINAL_BLOCKCHAIN_FILE,
                    BlockchainFactoryEnum.ORIGINAL);
            shortDataBlockchain = Blockchain.checkFromFile(Seting.ORIGINAL_BLOCKCHAIN_FILE);
            blockchainSize = (int) shortDataBlockchain.getSize();
            blockchainValid = shortDataBlockchain.isValidation();


            //если блокчейн работает то продолжить
            if (!tempBlockchain.validatedBlockchain()) {
                text = "wrong chain: неправильный блокчейн, добыча прекращена";
//            model.addAttribute("text", text);
                return "wrong blockchain";
            }


            //если размер блокчейна меньше или равно единице, сохранить в файл генезис блок
            long index = tempBlockchain.sizeBlockhain();
            if (blockchain.sizeBlockhain() <= 1) {
                System.out.println("save genesis block");
                //сохранение генезис блока
                if (blockchain.sizeBlockhain() == 1) {
                    UtilsBlock.saveBLock(blockchain.genesisBlock(), Seting.ORIGINAL_BLOCKCHAIN_FILE);
                }

                //получить список балансов из файла
                List<String> signs = new ArrayList<>();
                balances = Mining.getBalances(Seting.ORIGINAL_BALANCE_FILE, blockchain, balances, signs);
                //удалить старые файлы баланса
                Mining.deleteFiles(Seting.ORIGINAL_BALANCE_FILE);

                //сохранить балансы
                SaveBalances.saveBalances(balances, Seting.ORIGINAL_BALANCE_FILE);
                blockchain = Mining.getBlockchain(
                        Seting.ORIGINAL_BLOCKCHAIN_FILE,
                        BlockchainFactoryEnum.ORIGINAL);


            }
            //скачать список балансов из файла
            System.out.println("BasisController: minining: read list balance");
            balances = SaveBalances.readLineObject(Seting.ORIGINAL_BALANCE_FILE);

            //получить счет майнера

            miner = balances.get(User.getUserAddress());
            minerShow = miner;
            System.out.println("BasisController: mining: account miner: " + miner);
            if (miner == null) {
                //если в блокчейне не было баланса, то баланс равен нулю
                miner = new Account(User.getUserAddress(), 0, 0);
            }

            //транзакции которые мы добавили в блок и теперь нужно удалить из файла, в папке resources/transactions
            List<DtoTransaction> temporaryDtoList = AllTransactions.getInstance();
            //отказ от дублирующих транзакций
            temporaryDtoList = UtilsBlock.validDto(tempBlockchain.getBlockchainList(), temporaryDtoList);


            //раз в три для очищяет файлы в папке resources/sendedTransaction данная папка
            //хранит уже добавленые в блокчейн транзации, чтобы повторно не добавлять в
            //в блок уже добавленные транзакции

            AllTransactions.clearUsedTransaction(AllTransactions.getInsanceSended());
            System.out.println("BasisController: start mine:");

            //Сам процесс Майнинга
            //DIFFICULTY_ADJUSTMENT_INTERVAL как часто происходит коррекция
            //BLOCK_GENERATION_INTERVAL как часто должен находить блок
            //temporaryDtoList добавляет транзакции в блок
            Block block = Mining.miningDay(
                    miner,
                    tempBlockchain,
                    Seting.BLOCK_GENERATION_INTERVAL,
                    Seting.DIFFICULTY_ADJUSTMENT_INTERVAL,
                    temporaryDtoList,
                    balances,
                    index
            );
            System.out.println("BasisController: finish mine:");

            //нужна для корректировки сложности
            int diff = Seting.DIFFICULTY_ADJUSTMENT_INTERVAL;
            //Тестирование блока
            List<Block> testingValidationsBlock = null;

            if (tempBlockchain.sizeBlockhain() > diff) {

                testingValidationsBlock = tempBlockchain.subBlock(tempBlockchain.sizeBlockhain() - diff, tempBlockchain.sizeBlockhain());
            } else {
                testingValidationsBlock = tempBlockchain.clone();
            }
            //проверяет последние 288 блоков на валидность.
            if (testingValidationsBlock.size() > 1) {
                boolean validationTesting = UtilsBlock.validationOneBlock(
                        tempBlockchain.genesisBlock().getFounderAddress(),
                        testingValidationsBlock.get(testingValidationsBlock.size() - 1),
                        block,
                        Seting.BLOCK_GENERATION_INTERVAL,
                        diff,
                        testingValidationsBlock);

                if (validationTesting == false) {
                    System.out.println("wrong validation block: " + validationTesting);
                    System.out.println("index block: " + block.getIndex());
                    text = "wrong validation";
                }
                testingValidationsBlock.add(block.clone());
            }

            //добавляет последний блок в блокчейн
            tempBlockchain.addBlock(block);

            sendAllBlocksToStorage(tempBlockchain.getBlockchainList());
            tempBlockchain.setBlockchainList(new ArrayList<>());


            //отправить адресса
//        sendAddress();
            text = "success: блок успешно добыт";
//        model.addAttribute("text", text);
        } finally {
            mining = false;
        }
        return "ok";
    }

    /**
     * Стартует добычу, начинает майнинг
     */
    @GetMapping("/mine")
    public synchronized String mine(Model model) throws NoSuchAlgorithmException, InvalidKeySpecException, IOException, SignatureException, NoSuchProviderException, InvalidKeyException, JSONException, CloneNotSupportedException {
        mining();

        return "redirect:/mining";

    }


    @GetMapping("/testBlock")
    @ResponseBody
    public List<DtoTransaction> testBlock() throws IOException, CloneNotSupportedException, NoSuchAlgorithmException, InvalidKeySpecException, SignatureException, NoSuchProviderException, InvalidKeyException {
        List<DtoTransaction> temporaryDtoList = AllTransactions.getInstance();

        //отказ от дублирующих транзакций
        temporaryDtoList = UtilsBlock.validDto(blockchain.getBlockchainList(), temporaryDtoList);
        System.out.println("after: " + temporaryDtoList);
//        list.add(blockList);
        return temporaryDtoList;
    }

    @GetMapping("testBlock1")
    @ResponseBody
    public List<DtoTransaction> testBlock1() throws CloneNotSupportedException, IOException, NoSuchAlgorithmException, InvalidKeySpecException, SignatureException, NoSuchProviderException, InvalidKeyException {

        return  AllTransactions.getInstance();
    }

    @GetMapping("/processUpdating")
    public String processUpdating(Model model) {
        model.addAttribute("isMining", isMining());
        model.addAttribute("isUpdating", isUpdating());
        if (minerShow == null) {
            model.addAttribute("address", "balance has not loaded yet");
            model.addAttribute("dollar", "balance has not loaded yet");
            model.addAttribute("stock", "balance has not loaded yet");
        }else {
            DecimalFormat decimalFormat = new DecimalFormat("#.################");
            String dollar = decimalFormat.format(minerShow.getDigitalDollarBalance());
            String stock = decimalFormat.format(minerShow.getDigitalStockBalance());
            model.addAttribute("address", minerShow.getAccount());
            model.addAttribute("dollar", dollar);
            model.addAttribute("stock", stock);

        }


        return "processUpdating";
    }
}


