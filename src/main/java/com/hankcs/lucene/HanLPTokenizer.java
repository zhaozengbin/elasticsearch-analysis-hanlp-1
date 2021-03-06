package com.hankcs.lucene;


import com.hankcs.hanlp.collection.trie.bintrie.BinTrie;
import com.hankcs.hanlp.corpus.tag.Nature;
import com.hankcs.hanlp.seg.Segment;
import com.hankcs.hanlp.seg.common.Term;

import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Set;

/**
 * Tokenizer，抄袭ansj的
 * see https://github.com/hankcs/hanlp-lucene-plugin
 */
public class HanLPTokenizer extends Tokenizer {

    // 当前词
    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    // 偏移量
    private final OffsetAttribute offsetAtt = addAttribute(OffsetAttribute.class);
    // 距离
    private final PositionIncrementAttribute positionAttr = addAttribute(PositionIncrementAttribute.class);
    private final PorterStemmer stemmer = new PorterStemmer();
    // 词性
    private TypeAttribute typeAtt = addAttribute(TypeAttribute.class);
    private SegmentWrapper segment;
    private BinTrie<String> filter;
    private boolean enablePorterStemming;

    /**
     * 单文档当前所在的总offset，当reset（切换multi-value fields中的value）的时候不清零，在end（切换field）时清零
     */
    private int totalOffset = 0;

    /**
     * @param segment              HanLP中的某个分词器
     * @param filter               停用词
     * @param enablePorterStemming 英文原型转换
     */
    public HanLPTokenizer(Segment segment, Set<String> filter, boolean enablePorterStemming) {
        super();
        this.segment = new SegmentWrapper(input, segment);
        if (filter != null && filter.size() > 0) {
            this.filter = new BinTrie<String>();
            for (String stopWord : filter) {
                this.filter.put(stopWord, null);
            }
        }
        this.enablePorterStemming = enablePorterStemming;
    }

    @Override
    public boolean incrementToken() throws IOException {
        clearAttributes();
        int position = 0;
        Term term;
        boolean un_increased = true;
        do {
            term = segment.next();
            if (term == null) {
                break;
            }
            if (enablePorterStemming && term.nature == Nature.nx) {
                term.word = stemmer.stem(term.word);
            }

            if (filter != null && filter.containsKey(term.word)) {
                continue;
            } else {
                ++position;
                un_increased = false;
            }
        }
        while (un_increased);

        if (term != null) {
            positionAttr.setPositionIncrement(position);
            termAtt.setEmpty().append(term.word);
            offsetAtt.setOffset(correctOffset(totalOffset + term.offset),
                                correctOffset(totalOffset + term.offset + term.word.length()));
            typeAtt.setType(term.nature == null ? "null" : term.nature.toString());
            return true;
        } else {
            totalOffset += segment.offset;
            return false;
        }
    }

    @Override
    public void end() throws IOException {
        super.end();
        offsetAtt.setOffset(totalOffset, totalOffset);
        totalOffset = 0;
    }

    /**
     * 必须重载的方法，否则在批量索引文件时将会导致文件索引失败
     */
    @Override
    public void reset() throws IOException {
        super.reset();
        segment.reset(new BufferedReader(this.input));
    }

}
