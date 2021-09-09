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
package brut.androlib.res.decoder;

import brut.androlib.res.xml.ResXmlEncoders;
import brut.util.ExtDataInput;
import com.google.common.annotations.VisibleForTesting;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.logging.Logger;

/**
 * String块
 */
public class StringBlock {

    /**
     * Reads whole (including chunk type) string block from stream. Stream must
     * be at the chunk type.
     * 从流中读取整个字符串块(包括块类型)。流必须在块类型。
     *
     * @param reader ExtDataInput
     * @return StringBlock
     * @throws IOException Parsing resources.arsc error
     */
    public static StringBlock read(ExtDataInput reader) throws IOException {
//        跳过  头类型+头大小 ，以及可能存在的 0x00000000
        reader.skipCheckChunkTypeInt(CHUNK_STRINGPOOL_TYPE, CHUNK_NULL_TYPE);
//        该块的总大小(以字节为单位)
        int chunkSize = reader.readInt();

        // ResStringPool_header
//        此池中的字符串数
        int stringCount = reader.readInt();
//        池中样式span数组的数目
        int styleCount = reader.readInt();
//        标记
        int flags = reader.readInt();
//        字符串数据头的索引
        int stringsOffset = reader.readInt();
//        样式数据头部的索引
        int stylesOffset = reader.readInt();

//        字符串块
        StringBlock block = new StringBlock();
//        编码模式
        block.m_isUTF8 = (flags & UTF8_FLAG) != 0;
//        stringOffsets ， 字符串偏移量
        block.m_stringOffsets = reader.readIntArray(stringCount);

        if (styleCount != 0) {
//            样式偏移量
            block.m_styleOffsets = reader.readIntArray(styleCount);
        }


//        计算size
        int size = ((stylesOffset == 0) ? chunkSize : stylesOffset) - stringsOffset;
//        整个ResStringPool
        block.m_strings = new byte[size];
//        读取ResStringPool的所有数据
        reader.readFully(block.m_strings);

        if (stylesOffset != 0) {
//            样式数据头部的索引 不为 0 ，计算样式大小
            size = (chunkSize - stylesOffset);
            block.m_styles = reader.readIntArray(size / 4);

            // read remaining bytes
            int remaining = size % 4;
            if (remaining >= 1) {
                while (remaining-- > 0) {
                    reader.readByte();
                }
            }
        }

        return block;
    }

    /**
     * Returns raw string (without any styling information) at specified index.
     * 在指定的索引处返回原始字符串(没有任何样式信息)。
     *
     * @param index int
     * @return String
     */
    public String getString(int index) {
        if (index < 0 || m_stringOffsets == null || index >= m_stringOffsets.length) {
            return null;
        }
        int offset = m_stringOffsets[index];
        int length;

        int[] val;
        if (m_isUTF8) {
            val = getUtf8(m_strings, offset);
            offset = val[0];
        } else {
            val = getUtf16(m_strings, offset);
            offset += val[0];
        }
        length = val[1];
        return decodeString(offset, length);
    }

    /**
     * Returns string with style tags (html-like).
     * 返回带有样式标签的字符串(类似html)。
     *
     * @param index int
     * @return String
     */
    public String getHTML(int index) {
        String raw = getString(index);
        if (raw == null) {
            return null;
        }
        int[] style = getStyle(index);
        if (style == null) {
            return ResXmlEncoders.escapeXmlChars(raw);
        }

        // If the returned style is further in string, than string length. Lets skip it.
//        如果返回的样式大于字符串长度。让我们跳过它。
        if (style[1] > raw.length()) {
            return ResXmlEncoders.escapeXmlChars(raw);
        }
        StringBuilder html = new StringBuilder(raw.length() + 32);
        int[] opened = new int[style.length / 3];
        boolean[] unclosed = new boolean[style.length / 3];
        int offset = 0, depth = 0;
        while (true) {
            int i = -1, j;
            for (j = 0; j != style.length; j += 3) {
                if (style[j + 1] == -1) {
                    continue;
                }
                if (i == -1 || style[i + 1] > style[j + 1]) {
                    i = j;
                }
            }
            int start = ((i != -1) ? style[i + 1] : raw.length());
            for (j = depth - 1; j >= 0; j--) {
                int last = opened[j];
                int end = style[last + 2];
                if (end >= start) {
                    if (style[last + 1] == -1 && end != -1) {
                        unclosed[j] = true;
                    }
                    break;
                }
                if (offset <= end) {
                    html.append(ResXmlEncoders.escapeXmlChars(raw.substring(offset, end + 1)));
                    offset = end + 1;
                }
                outputStyleTag(getString(style[last]), html, true);
            }
            depth = j + 1;
            if (offset < start) {
                html.append(ResXmlEncoders.escapeXmlChars(raw.substring(offset, start)));
                if (j >= 0 && unclosed.length >= j && unclosed[j]) {
                    if (unclosed.length > (j + 1) && unclosed[j + 1] || unclosed.length == 1) {
                        outputStyleTag(getString(style[opened[j]]), html, true);
                    }
                }
                offset = start;
            }
            if (i == -1) {
                break;
            }
            outputStyleTag(getString(style[i]), html, false);
            style[i + 1] = -1;
            opened[depth++] = i;
        }
        return html.toString();
    }

    /**
     * 输出样式标签
     *
     * @param tag     tag
     * @param builder builder
     * @param close   close
     */
    private void outputStyleTag(String tag, StringBuilder builder, boolean close) {
        builder.append('<');
        if (close) {
            builder.append('/');
        }

        int pos = tag.indexOf(';');
        if (pos == -1) {
            builder.append(tag);
        } else {
            builder.append(tag.substring(0, pos));
            if (!close) {
                boolean loop = true;
                while (loop) {
                    int pos2 = tag.indexOf('=', pos + 1);

                    // malformed style information will cause crash. so
                    // prematurely end style tags, if recreation
                    // cannot be created.
//                    不正确的样式信息将导致崩溃。所以
//                    过早结束样式标签，如果重新创建
//                     无法创建。
                    if (pos2 != -1) {
                        builder.append(' ').append(tag.substring(pos + 1, pos2)).append("=\"");
                        pos = tag.indexOf(';', pos2 + 1);

                        String val;
                        if (pos != -1) {
                            val = tag.substring(pos2 + 1, pos);
                        } else {
                            loop = false;
                            val = tag.substring(pos2 + 1);
                        }

                        builder.append(ResXmlEncoders.escapeXmlChars(val)).append('"');
                    } else {
                        loop = false;
                    }

                }
            }
        }
        builder.append('>');
    }

    /**
     * Finds index of the string. Returns -1 if the string was not found.
     * 查找字符串的索引。如果未找到字符串，则返回-1。
     *
     * @param string String to index location of 索引的位置
     * @return int (Returns -1 if not found) 如果未找到则返回-1
     */
    public int find(String string) {
        if (string == null) {
            return -1;
        }
        for (int i = 0; i != m_stringOffsets.length; ++i) {
            int offset = m_stringOffsets[i];
            int length = getShort(m_strings, offset);
            if (length != string.length()) {
                continue;
            }
            int j = 0;
            for (; j != length; ++j) {
                offset += 2;
                if (string.charAt(j) != getShort(m_strings, offset)) {
                    break;
                }
            }
            if (j == length) {
                return i;
            }
        }
        return -1;
    }

    private StringBlock() {
    }

    @VisibleForTesting
    StringBlock(byte[] strings, boolean isUTF8) {
        m_strings = strings;
        m_isUTF8 = isUTF8;
    }

    /**
     * Returns style information - array of int triplets, where in each triplet:
     * * first int is index of tag name ('b','i', etc.) * second int is tag
     * start index in string * third int is tag end index in string
     * <p>
     * 返回样式信息——int三元组的数组，其中每个三元组:
     * 第一个int是标签名称('b'，'i'等)的索引*第二个int是标签
     * string * third int的起始索引是string中的标签结束索引
     */
    private int[] getStyle(int index) {
        if (m_styleOffsets == null || m_styles == null || index >= m_styleOffsets.length) {
            return null;
        }
        int offset = m_styleOffsets[index] / 4;
        int count = 0;
        int[] style;

        for (int i = offset; i < m_styles.length; ++i) {
            if (m_styles[i] == -1) {
                break;
            }
            count += 1;
        }

        if (count == 0 || (count % 3) != 0) {
            return null;
        }
        style = new int[count];

        for (int i = offset, j = 0; i < m_styles.length; ) {
            if (m_styles[i] == -1) {
                break;
            }
            style[j++] = m_styles[i++];
        }
        return style;
    }

    /**
     * 解码字符串，
     *
     * @param offset 位移
     * @param length 字符串长度
     * @return
     */
    @VisibleForTesting
    String decodeString(int offset, int length) {
        try {
            final ByteBuffer wrappedBuffer = ByteBuffer.wrap(m_strings, offset, length);
            return (m_isUTF8 ? UTF8_DECODER : UTF16LE_DECODER).decode(wrappedBuffer).toString();
        } catch (CharacterCodingException ex) {
            if (!m_isUTF8) {
                LOGGER.warning("Failed to decode a string at offset " + offset + " of length " + length);
                return null;
            }
        }

        try {
            final ByteBuffer wrappedBufferRetry = ByteBuffer.wrap(m_strings, offset, length);
            // in some places, Android uses 3-byte UTF-8 sequences instead of 4-bytes.
            // If decoding failed, we try to use CESU-8 decoder, which is closer to what Android actually uses.
//            在某些地方，Android使用3字节的UTF-8序列而不是4字节。
//            如果解码失败，我们尝试使用CESU-8解码器，更接近Android实际使用的解码器。
            return CESU8_DECODER.decode(wrappedBufferRetry).toString();
        } catch (CharacterCodingException e) {
            LOGGER.warning("Failed to decode a string with CESU-8 decoder.");
            return null;
        }
    }

    /**
     * 获取Short长度
     *
     * @param array  byte[]
     * @param offset int
     * @return int
     */
    private static final int getShort(byte[] array, int offset) {
        return (array[offset + 1] & 0xff) << 8 | array[offset] & 0xff;
    }

    /**
     * 获得UTF8长度的数组 （4字节）
     *
     * @param array  array
     * @param offset offset
     * @return int[]
     */
    private static final int[] getUtf8(byte[] array, int offset) {
        int val = array[offset];
        int length;
        // We skip the utf16 length of the string
//        我们跳过字符串的utf16长度
        if ((val & 0x80) != 0) {
            offset += 2;
        } else {
            offset += 1;
        }
        // And we read only the utf-8 encoded length of the string
//        我们只读取utf-8编码的字符串长度
        val = array[offset];
        offset += 1;
        if ((val & 0x80) != 0) {
            int low = (array[offset] & 0xFF);
            length = ((val & 0x7F) << 8) + low;
            offset += 1;
        } else {
            length = val;
        }
        return new int[]{offset, length};
    }

    /**
     * 获取UTF16长度的数组
     *
     * @param array  array
     * @param offset offset
     * @return int[]
     */
    private static final int[] getUtf16(byte[] array, int offset) {
        int val = ((array[offset + 1] & 0xFF) << 8 | array[offset] & 0xFF);

        if ((val & 0x8000) != 0) {
            int high = (array[offset + 3] & 0xFF) << 8;
            int low = (array[offset + 2] & 0xFF);
            int len_value = ((val & 0x7FFF) << 16) + (high + low);
            return new int[]{4, len_value * 2};

        }
        return new int[]{2, val * 2};
    }

    /**
     * 字符串偏移量数组
     */
    private int[] m_stringOffsets;
    /**
     * 字符串数组
     */
    private byte[] m_strings;
    /**
     * 样式偏移量数组
     */
    private int[] m_styleOffsets;
    /**
     * 样式
     */
    private int[] m_styles;
    /**
     * 是否UTF8编码
     */
    private boolean m_isUTF8;

    private final CharsetDecoder UTF16LE_DECODER = Charset.forName("UTF-16LE").newDecoder();
    private final CharsetDecoder UTF8_DECODER = Charset.forName("UTF-8").newDecoder();
    private final CharsetDecoder CESU8_DECODER = Charset.forName("CESU8").newDecoder();
    private static final Logger LOGGER = Logger.getLogger(StringBlock.class.getName());

    // ResChunk_header = header.type (0x0001) + header.headerSize (0x001C)
//    头类型+头大小
    private static final int CHUNK_STRINGPOOL_TYPE = 0x001C0001;
    //    空位
    private static final int CHUNK_NULL_TYPE = 0x00000000;
    private static final int UTF8_FLAG = 0x00000100;
}
