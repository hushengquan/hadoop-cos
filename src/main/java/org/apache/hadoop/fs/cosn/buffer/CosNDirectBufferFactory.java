package org.apache.hadoop.fs.cosn.buffer;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.hadoop.util.DirectBufferPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CosNDirectBufferFactory implements CosNBufferFactory {
    private static final Logger LOG =
            LoggerFactory.getLogger(CosNDirectBufferFactory.class);

    private final DirectBufferPool directBufferPool = new DirectBufferPool();

    @Override
    public CosNByteBuffer create(int size) {
        ByteBuffer byteBuffer = this.directBufferPool.getBuffer(size);
        return (new CosNDirectBuffer(byteBuffer)).clear();
    }

    @Override
    public void release(CosNByteBuffer cosNByteBuffer) {
        if (null == cosNByteBuffer) {
            LOG.debug("The buffer returned is null. Ignore it.");
            return;
        }

        if (null == ((CosNDirectBuffer)cosNByteBuffer).getByteBuffer()) {
            LOG.warn("The byte buffer returned is null. can not be released.");
            return;
        }
        this.directBufferPool.returnBuffer(((CosNDirectBuffer)cosNByteBuffer).getByteBuffer());

        try {
            cosNByteBuffer.close();
        } catch (IOException e) {
            LOG.error("Release the direct byte buffer failed.", e);
        }
    }
}
