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
package brut.androlib.mod;

import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.RecognitionException;
import org.antlr.runtime.Token;
import org.antlr.runtime.tree.CommonTree;
import org.antlr.runtime.tree.CommonTreeNodeStream;
import org.jf.dexlib2.writer.builder.DexBuilder;
import org.jf.smali.smaliFlexLexer;
import org.jf.smali.smaliParser;
import org.jf.smali.smaliTreeWalker;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * smali 模型
 */
public class SmaliMod {

    /**
     * 将smali文件 整理成dex文件
     *
     * @param smaliFile     smaliFile
     * @param dexBuilder    dexBuilder dex构建
     * @param apiLevel      apiLevel
     * @param verboseErrors verboseErrors 是否详细日志
     * @param printTokens   printTokens 打印Tokens
     * @return 整理结果
     * @throws IOException          IO异常
     * @throws RecognitionException Recognition异常
     */
    public static boolean assembleSmaliFile(File smaliFile, DexBuilder dexBuilder, int apiLevel, boolean verboseErrors,
                                            boolean printTokens) throws IOException, RecognitionException {

//        antlr解析，CommonTokenStream
        CommonTokenStream tokens;
//        smali文件分析
        smaliFlexLexer lexer;

        InputStream is = new FileInputStream(smaliFile);
        InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);

//        分析smali
        lexer = new smaliFlexLexer(reader, apiLevel);
//        设置sourceFile
        (lexer).setSourceFile(smaliFile);
//        antlr解析smali
        tokens = new CommonTokenStream(lexer);

        if (printTokens) {
//            打印antlr的分析
            tokens.getTokens();

            for (int i = 0; i < tokens.size(); i++) {
                Token token = tokens.get(i);
                if (token.getChannel() == smaliParser.HIDDEN) {
                    continue;
                }

                System.out.println(smaliParser.tokenNames[token.getType()] + ": " + token.getText());
            }
        }

//        从tokens里面解析smali
        smaliParser parser = new smaliParser(tokens);
//        设置smali 的apiLevel
        parser.setApiLevel(apiLevel);
//        是否打印纤细日志
        parser.setVerboseErrors(verboseErrors);

        smaliParser.smali_file_return result = parser.smali_file();

        if (parser.getNumberOfSyntaxErrors() > 0 || lexer.getNumberOfSyntaxErrors() > 0) {
//           解析语法错误或分析语法错误
            is.close();
            reader.close();
            return false;
        }

//        获取解析后的树
        CommonTree t = result.getTree();

//        转换为流
        CommonTreeNodeStream treeStream = new CommonTreeNodeStream(t);
        treeStream.setTokenStream(tokens);

        smaliTreeWalker dexGen = new smaliTreeWalker(treeStream);
        dexGen.setApiLevel(apiLevel);
        dexGen.setVerboseErrors(verboseErrors);
        dexGen.setDexBuilder(dexBuilder);
        dexGen.smali_file();

        is.close();
        reader.close();

        return dexGen.getNumberOfSyntaxErrors() == 0;
    }
}
