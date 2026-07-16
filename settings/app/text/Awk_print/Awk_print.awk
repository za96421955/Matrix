BEGIN {
    str = ARGV[1]
    # 用 match 找到模式出现的位置，RSTART 和 RLENGTH 是内置变量
    if (match(str, /"print" *: *"[^"]*"/)) {
        matched = substr(str, RSTART, RLENGTH)
        # 从匹配到的字符串中取出冒号后的双引号内容
        gsub(/^.*"print" *: *"/, "", matched)
        gsub(/"$/, "", matched)
        print matched
    }
}