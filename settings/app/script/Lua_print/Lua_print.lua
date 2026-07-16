-- 简单字符串匹配，无需外部库
local json = arg[1]
local printValue = string.match(json, '"print"%s*:%s*"([^"]*)"')
print(printValue)