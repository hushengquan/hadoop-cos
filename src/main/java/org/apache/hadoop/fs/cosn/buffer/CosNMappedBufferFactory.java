package org.apache.hadoop.fs.cosn.buffer;

import org.apache.hadoop.fs.CosNUtils;
import org.apache.hadoop.fs.cosn.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


public class CosNMappedBufferFactory implements CosNBufferFactory {
    private static final Logger LOG =
            LoggerFactory.getLogger(CosNMappedBufferFactory.class);

    private final List<File> tmpDirs = new ArrayList<>();
    private final boolean deleteOnExit;

    public CosNMappedBufferFactory(String[] tmpDirList, boolean deleteOnExit) throws IOException {
        for (String tmpDir : tmpDirList) {
            File createDir = CosNUtils.createTempDir(tmpDir);
            tmpDirs.add(createDir);
        }
        this.deleteOnExit = deleteOnExit;
    }

    @Override
    public CosNByteBuffer create(int size) {
        return this.create(Constants.BLOCK_TMP_FILE_PREFIX,
                Constants.BLOCK_TMP_FILE_SUFFIX, size);
    }

    private final AtomicInteger currentIndex = new AtomicInteger();

    private File getTmpDir() {
        return tmpDirs.get(Math.abs(currentIndex.getAndIncrement() % tmpDirs.size()));
    }

    public CosNByteBuffer create(String prefix, String suffix, int size) {
        File tmpDir = getTmpDir();
        if (null == tmpDir) {
            LOG.error("The tmp dir is null. no mapped buffer will be created.");
            return null;
        }

        if (!tmpDir.exists()) {
            LOG.warn("The tmp dir does not exist.");
            // try to create the tmp directory.
            try {
                CosNUtils.createTempDir(tmpDir.getAbsolutePath());
            } catch (IOException e) {
                LOG.error("Try to create the tmp dir [{}] failed.", tmpDir.getAbsolutePath(), e);
                return null;
            }
        }

        try {
            File tmpFile = File.createTempFile(
                    Constants.BLOCK_TMP_FILE_PREFIX,
                    Constants.BLOCK_TMP_FILE_SUFFIX,
                    tmpDir
            );

            if (this.deleteOnExit) {
                tmpFile.deleteOnExit();
            }
            RandomAccessFile randomAccessFile = new RandomAccessFile(tmpFile,
                    "rw");
            randomAccessFile.setLength(size);
            MappedByteBuffer buf =
                    randomAccessFile.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, size);
            return (null != buf) ? new CosNMappedBuffer(buf, randomAccessFile, tmpFile) : null;
        } catch (IOException e) {
            LOG.error("Create tmp file failed. Tmp dir: {}", tmpDir, e);
            return null;
        }
    }

    @Override
    public void release(CosNByteBuffer cosNByteBuffer) {
        if (null == cosNByteBuffer) {
            LOG.debug("The buffer returned is null. Ignore it.");
            return;
        }

        try {
            cosNByteBuffer.close();
        } catch (IOException e) {
            LOG.error("Release the mapped byte buffer failed.", e);
        }
    }
}
