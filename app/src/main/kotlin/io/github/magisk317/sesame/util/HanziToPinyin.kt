package io.github.magisk317.sesame.util

import java.text.Collator
import java.util.ArrayList
import java.util.Locale

/** Pinyin tokenizer with the original dictionary preserved as data. */
class HanziToPinyin private constructor(private val hasChinaCollator: Boolean) {
    class Token @JvmOverloads constructor(
        @JvmField var type: Int = UNKNOWN,
        @JvmField var source: String = "",
        @JvmField var target: String = "",
    ) {
        companion object {
            const val LATIN = 1
            const val PINYIN = 2
            const val UNKNOWN = 3
        }
    }

    private fun tokenFor(character: Char): Token {
        return try {
            val letter = character.toString()
            if (character.code < 256) return Token(Token.LATIN, letter, letter)
            if (character.code < FIRST_UNIHAN.code) return Token(Token.UNKNOWN, letter, letter)

            val offset = when {
                COLLATOR.compare(letter, FIRST_PINYIN_UNIHAN) < 0 ->
                    return Token(Token.UNKNOWN, letter, letter)
                COLLATOR.compare(letter, FIRST_PINYIN_UNIHAN) == 0 -> 0
                COLLATOR.compare(letter, LAST_PINYIN_UNIHAN) > 0 ->
                    return Token(Token.UNKNOWN, letter, letter)
                COLLATOR.compare(letter, LAST_PINYIN_UNIHAN) == 0 -> unihans.lastIndex
                else -> binarySearch(letter)
            }
            Token(Token.PINYIN, letter, pinyins[offset])
        } catch (error: Throwable) {
            StructuredLog.record(TAG, "字符'$character'转换失败")
            throw error
        }
    }

    private fun binarySearch(letter: String): Int {
        var begin = 0
        var end = unihans.lastIndex
        var offset = 0
        while (begin <= end) {
            offset = (begin + end) / 2
            when {
                COLLATOR.compare(letter, unihans[offset].toString()) == 0 -> return offset
                COLLATOR.compare(letter, unihans[offset].toString()) > 0 -> begin = offset + 1
                else -> end = offset - 1
            }
        }
        return offset
    }

    fun get(input: String?): ArrayList<Token> {
        val tokens = ArrayList<Token>()
        if (!hasChinaCollator || input.isNullOrEmpty()) return tokens
        try {
            val buffer = StringBuilder()
            var tokenType = Token.LATIN
            input.forEach { character ->
                when {
                    character == ' ' -> {
                        if (buffer.isNotEmpty()) addToken(buffer, tokens, tokenType)
                    }
                    character.code < 256 -> {
                        if (tokenType != Token.LATIN && buffer.isNotEmpty()) addToken(buffer, tokens, tokenType)
                        tokenType = Token.LATIN
                        buffer.append(character)
                    }
                    character.code < FIRST_UNIHAN.code -> {
                        if (tokenType != Token.UNKNOWN && buffer.isNotEmpty()) addToken(buffer, tokens, tokenType)
                        tokenType = Token.UNKNOWN
                        buffer.append(character)
                    }
                    else -> {
                        val token = tokenFor(character)
                        if (token.type == Token.PINYIN) {
                            if (buffer.isNotEmpty()) addToken(buffer, tokens, tokenType)
                            tokens += token
                            tokenType = Token.PINYIN
                        } else {
                            if (tokenType != token.type && buffer.isNotEmpty()) addToken(buffer, tokens, tokenType)
                            tokenType = token.type
                            buffer.append(character)
                        }
                    }
                }
            }
            if (buffer.isNotEmpty()) addToken(buffer, tokens, tokenType)
            return tokens
        } catch (error: Throwable) {
            StructuredLog.record(TAG, "字符串\"$input\"转换失败")
            throw error
        }
    }

    private fun addToken(buffer: StringBuilder, tokens: ArrayList<Token>, type: Int) {
        val value = buffer.toString()
        tokens += Token(type, value, value)
        buffer.setLength(0)
    }

    companion object {
        private const val TAG = "HanziToPinyin"
        private const val FIRST_PINYIN_UNIHAN = "阿"
        private const val LAST_PINYIN_UNIHAN = "蓙"
        private const val FIRST_UNIHAN = '㐀'
        private val COLLATOR: Collator = Collator.getInstance(Locale.CHINA)

        @Volatile private var instance: HanziToPinyin? = null

        @JvmStatic
        fun getInstance(): HanziToPinyin =
            instance ?: synchronized(this) { instance ?: HanziToPinyin(true).also { instance = it } }
    }

private val unihans = charArrayOf(
            '呵', '哎', '安', '肮', '凹',
            '八', '挀', '扳', '邦', '包', '卑', '奔', '伻',
            '屄', '边', '标', '憋', '邠', '槟', '癶', '峬',
            '嚓', '婇', '飡', '仓', '操', '冊', '嵾', '噌',
            '叉', '钗', '辿', '伥', '抄', '车', '抻', '柽',
            '吃', '充', '抽', '出', '欻', '揣', '川', '疮',
            '吹', '杶', '逴', '疵', '匆', '凑', '粗', '汆',
            '崔', '邨', '搓', '咑', '大', '疸', '当', '刀',
            '淂', '得', '扥', '灯', '氐', '嗲', '甸', '刁',
            '爹', '仃', '丟', '东', '唗', '嘟', '偳', '堆',
            '鐓', '多', '婀', '诶', '奀', '鞥', '而', '发',
            '帆', '方', '飞', '分', '丰', '覅', '仏', '紑',
            '伕', '旮', '该', '甘', '冈', '皋', '戈', '給',
            '根', '庚', '工', '勾', '估', '瓜', '罫', '关',
            '光', '归', '衮', '呙', '哈', '咳', '顸', '苀',
            '蒿', '诃', '黒', '拫', '亨', '噷', '吽', '齁',
            '匢', '花', '怀', '犿', '巟', '灰', '昏', '吙',
            '丌', '加', '戋', '江', '艽', '阶', '巾', '劤',
            '冂', '勼', '匊', '娟', '噘', '军', '咔', '开',
            '刊', '闶', '尻', '匼', '剋', '肯', '阬', '空',
            '抠', '刳', '夸', '蒯', '宽', '匡', '亏', '坤',
            '扩', '垃', '来', '兰', '啷', '捞', '仂', '勒',
            '塄', '刕', '倆', '奁', '良', '撩', '列', '拎',
            '〇', '溜', '龙', '瞜', '噜', '娈', '畧', '抡',
            '罗', '呣', '妈', '霾', '嫚', '邙', '猫', '麼',
            '沒', '门', '甿', '咪', '眠', '喵', '咩', '民',
            '名', '谬', '摸', '哞', '毪', '拏', '孻', '囡',
            '囊', '孬', '讷', '馁', '恁', '能', '妮', '拈',
            '嬢', '鸟', '捏', '您', '宁', '妞', '农', '羺',
            '奴', '奻', '虐', '挪', '喔', '讴', '趴', '拍',
            '眅', '乓', '抛', '呸', '喷', '匉', '丕', '偏',
            '剽', '氕', '姘', '乒', '钋', '剖', '仆', '七',
            '掐', '千', '呛', '悄', '癿', '侵', '靑', '邛',
            '丘', '曲', '弮', '缺', '夋', '呥', '穣', '娆',
            '惹', '人', '扔', '日', '茸', '厹', '如', '堧',
            '桵', '闰', '若', '仨', '毢', '三', '桒', '掻',
            '色', '森', '僧', '杀', '筛', '山', '伤', '弰',
            '奢', '申', '升', '尸', '収', '书', '刷', '摔',
            '闩', '双', '谁', '吮', '妁', '厶', '忪', '捜',
            '苏', '狻', '夊', '孙', '唆', '他', '苔', '坍',
            '铴', '夲', '忑', '熥', '剔', '天', '佻', '帖',
            '厅', '囲', '偷', '鋀', '湍', '推', '吞', '托',
            '挖', '歪', '弯', '尪', '危', '塭', '翁', '挝',
            '兀', '夕', '虾', '仚', '乡', '灱', '些', '心',
            '星', '凶', '休', '旴', '轩', '疶', '勋', '丫',
            '恹', '央', '幺', '耶', '一', '欭', '应', '哟',
            '佣', '优', '扜', '鸢', '曰', '晕', '匝', '災',
            '糌', '牂', '傮', '则', '贼', '怎', '増', '吒',
            '捚', '沾', '张', '钊', '蜇', '贞', '争', '之',
            '中', '州', '朱', '抓', '跩', '专', '妆', '隹',
            '宒', '卓', '孜', '宗', '邹', '租', '钻', '厜',
            '尊', '昨',)

private val pinyins = arrayOf(
    "A",
    "AI",
    "AN",
    "ANG",
    "AO",
    "BA",
    "BAI",
    "BAN",
    "BANG",
    "BAO",
    "BEI",
    "BEN",
    "BENG",
    "BI",
    "BIAN",
    "BIAO",
    "BIE",
    "BIN",
    "BING",
    "BO",
    "BU",
    "CA",
    "CAI",
    "CAN",
    "CANG",
    "CAO",
    "CE",
    "CEN",
    "CENG",
    "CHA",
    "CHAI",
    "CHAN",
    "CHANG",
    "CHAO",
    "CHE",
    "CHEN",
    "CHENG",
    "CHI",
    "CHONG",
    "CHOU",
    "CHU",
    "CHUA",
    "CHUAI",
    "CHUAN",
    "CHUANG",
    "CHUI",
    "CHUN",
    "CHUO",
    "CI",
    "CONG",
    "COU",
    "CU",
    "CUAN",
    "CUI",
    "CUN",
    "CUO",
    "DA",
    "DAI",
    "DAN",
    "DANG",
    "DAO",
    "DE",
    "DEI",
    "DEN",
    "DENG",
    "DI",
    "DIA",
    "DIAN",
    "DIAO",
    "DIE",
    "DING",
    "DIU",
    "DONG",
    "DOU",
    "DU",
    "DUAN",
    "DUI",
    "DUN",
    "DUO",
    "E",
    "EI",
    "EN",
    "ENG",
    "ER",
    "FA",
    "FAN",
    "FANG",
    "FEI",
    "FEN",
    "FENG",
    "FIAO",
    "FO",
    "FOU",
    "FU",
    "GA",
    "GAI",
    "GAN",
    "GANG",
    "GAO",
    "GE",
    "GEI",
    "GEN",
    "GENG",
    "GONG",
    "GOU",
    "GU",
    "GUA",
    "GUAI",
    "GUAN",
    "GUANG",
    "GUI",
    "GUN",
    "GUO",
    "HA",
    "HAI",
    "HAN",
    "HANG",
    "HAO",
    "HE",
    "HEI",
    "HEN",
    "HENG",
    "HM",
    "HONG",
    "HOU",
    "HU",
    "HUA",
    "HUAI",
    "HUAN",
    "HUANG",
    "HUI",
    "HUN",
    "HUO",
    "JI",
    "JIA",
    "JIAN",
    "JIANG",
    "JIAO",
    "JIE",
    "JIN",
    "JING",
    "JIONG",
    "JIU",
    "JU",
    "JUAN",
    "JUE",
    "JUN",
    "KA",
    "KAI",
    "KAN",
    "KANG",
    "KAO",
    "KE",
    "KEI",
    "KEN",
    "KENG",
    "KONG",
    "KOU",
    "KU",
    "KUA",
    "KUAI",
    "KUAN",
    "KUANG",
    "KUI",
    "KUN",
    "KUO",
    "LA",
    "LAI",
    "LAN",
    "LANG",
    "LAO",
    "LE",
    "LEI",
    "LENG",
    "LI",
    "LIA",
    "LIAN",
    "LIANG",
    "LIAO",
    "LIE",
    "LIN",
    "LING",
    "LIU",
    "LONG",
    "LOU",
    "LU",
    "LUAN",
    "LUE",
    "LUN",
    "LUO",
    "M",
    "MA",
    "MAI",
    "MAN",
    "MANG",
    "MAO",
    "ME",
    "MEI",
    "MEN",
    "MENG",
    "MI",
    "MIAN",
    "MIAO",
    "MIE",
    "MIN",
    "MING",
    "MIU",
    "MO",
    "MOU",
    "MU",
    "NA",
    "NAI",
    "NAN",
    "NANG",
    "NAO",
    "NE",
    "NEI",
    "NEN",
    "NENG",
    "NI",
    "NIAN",
    "NIANG",
    "NIAO",
    "NIE",
    "NIN",
    "NING",
    "NIU",
    "NONG",
    "NOU",
    "NU",
    "NUAN",
    "NUE",
    "NUO",
    "O",
    "OU",
    "PA",
    "PAI",
    "PAN",
    "PANG",
    "PAO",
    "PEI",
    "PEN",
    "PENG",
    "PI",
    "PIAN",
    "PIAO",
    "PIE",
    "PIN",
    "PING",
    "PO",
    "POU",
    "PU",
    "QI",
    "QIA",
    "QIAN",
    "QIANG",
    "QIAO",
    "QIE",
    "QIN",
    "QING",
    "QIONG",
    "QIU",
    "QU",
    "QUAN",
    "QUE",
    "QUN",
    "RAN",
    "RANG",
    "RAO",
    "RE",
    "REN",
    "RENG",
    "RI",
    "RONG",
    "ROU",
    "RU",
    "RUAN",
    "RUI",
    "RUN",
    "RUO",
    "SA",
    "SAI",
    "SAN",
    "SANG",
    "SAO",
    "SE",
    "SEN",
    "SENG",
    "SHA",
    "SHAI",
    "SHAN",
    "SHANG",
    "SHAO",
    "SHE",
    "SHEN",
    "SHENG",
    "SHI",
    "SHOU",
    "SHU",
    "SHUA",
    "SHUAI",
    "SHUAN",
    "SHUANG",
    "SHUI",
    "SHUN",
    "SHUO",
    "SI",
    "SONG",
    "SOU",
    "SU",
    "SUAN",
    "SUI",
    "SUN",
    "SUO",
    "TA",
    "TAI",
    "TAN",
    "TANG",
    "TAO",
    "TE",
    "TENG",
    "TI",
    "TIAN",
    "TIAO",
    "TIE",
    "TING",
    "TONG",
    "TOU",
    "TU",
    "TUAN",
    "TUI",
    "TUN",
    "TUO",
    "WA",
    "WAI",
    "WAN",
    "WANG",
    "WEI",
    "WEN",
    "WENG",
    "WO",
    "WU",
    "XI",
    "XIA",
    "XIAN",
    "XIANG",
    "XIAO",
    "XIE",
    "XIN",
    "XING",
    "XIONG",
    "XIU",
    "XU",
    "XUAN",
    "XUE",
    "XUN",
    "YA",
    "YAN",
    "YANG",
    "YAO",
    "YE",
    "YI",
    "YIN",
    "YING",
    "YO",
    "YONG",
    "YOU",
    "YU",
    "YUAN",
    "YUE",
    "YUN",
    "ZA",
    "ZAI",
    "ZAN",
    "ZANG",
    "ZAO",
    "ZE",
    "ZEI",
    "ZEN",
    "ZENG",
    "ZHA",
    "ZHAI",
    "ZHAN",
    "ZHANG",
    "ZHAO",
    "ZHE",
    "ZHEN",
    "ZHENG",
    "ZHI",
    "ZHONG",
    "ZHOU",
    "ZHU",
    "ZHUA",
    "ZHUAI",
    "ZHUAN",
    "ZHUANG",
    "ZHUI",
    "ZHUN",
    "ZHUO",
    "ZI",
    "ZONG",
    "ZOU",
    "ZU",
    "ZUAN",
    "ZUI",
    "ZUN",
    "ZUO",
)

}
