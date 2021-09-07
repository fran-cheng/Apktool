/*
 *  Copyright (C) 2010 Ryszard Wiśniewski <brut.alll@gmail.com>
 *  Copyright (C) 2010 Connor Tumbleson <connor.tumbleson@gmail.com>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package brut.util;

import java.io.DataInput;
import java.io.IOException;

/**
 * 抽象 公有的读取数据方法
 */
public abstract class DataInputDelegate implements DataInput {
    /**
     * 当前的流
     */
    protected final DataInput mDelegate;

    public DataInputDelegate(DataInput delegate) {
        this.mDelegate = delegate;
    }

    /**
     * 跳过n个字节
     *
     * @param n 字节数
     * @return int 实际跳过的字节
     * @throws IOException IO异常
     */
    public int skipBytes(int n) throws IOException {
        return mDelegate.skipBytes(n);
    }

    /**
     * 读取无符合Short 长度
     *
     * @return 读取的无符号16位值。
     * @throws IOException IO异常
     */
    public int readUnsignedShort() throws IOException {
        return mDelegate.readUnsignedShort();
    }

    /**
     * 读取字节无符号
     *
     * @return int
     * @throws IOException IO异常
     */
    public int readUnsignedByte() throws IOException {
        return mDelegate.readUnsignedByte();
    }

    /**
     * 读取UTF字符串
     *
     * @return Unicode字符串。
     * @throws IOException IO异常
     */
    public String readUTF() throws IOException {
        return mDelegate.readUTF();
    }

    /**
     * 读取2个字节
     *
     * @return 读取的16位值。
     * @throws IOException IO异常
     */
    public short readShort() throws IOException {
        return mDelegate.readShort();
    }

    /**
     * 读取long
     *
     * @return {@code long}值读取。
     * @throws IOException IO异常
     */
    public long readLong() throws IOException {
        return mDelegate.readLong();
    }

    /**
     * 读取行
     *
     * @return 输入流中的下一行文本，或者{@code null}，如果文件的结尾是在读取一个字节之前遇到。
     * @throws IOException IO异常
     */
    public String readLine() throws IOException {
        return mDelegate.readLine();
    }

    /**
     * 读取4个字节
     *
     * @return int 读取的值
     * @throws IOException IO异常
     */
    public int readInt() throws IOException {
        return mDelegate.readInt();
    }

    public void readFully(byte[] b, int off, int len) throws IOException {
        mDelegate.readFully(b, off, len);
    }

    /**
     * 全部读取
     *
     * @param b byte[] 将数据读入的缓冲区。
     * @throws IOException IO异常
     */
    public void readFully(byte[] b) throws IOException {
        mDelegate.readFully(b);
    }

    /**
     * 读取float长度
     *
     * @return 读取的值
     * @throws IOException IO异常
     */
    public float readFloat() throws IOException {
        return mDelegate.readFloat();
    }

    /**
     * 读取double长度
     *
     * @return 读取的值
     * @throws IOException IO异常
     */
    public double readDouble() throws IOException {
        return mDelegate.readDouble();
    }

    /**
     * 读取char长度
     *
     * @return 读取的值
     * @throws IOException IO异常
     */
    public char readChar() throws IOException {
        return mDelegate.readChar();
    }

    /**
     * 读取byte长度
     *
     * @return 读取的值
     * @throws IOException IO异常
     */
    public byte readByte() throws IOException {
        return mDelegate.readByte();
    }

    /**
     * 读取boolean长度
     *
     * @return 读取的值
     * @throws IOException IO异常
     */
    public boolean readBoolean() throws IOException {
        return mDelegate.readBoolean();
    }
}
