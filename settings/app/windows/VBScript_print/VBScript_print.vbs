Function GetPrint(json)
    Dim regex, matches
    Set regex = New RegExp
    regex.Pattern = """print""\s*:\s*""([^""]*)"""
    Set matches = regex.Execute(json)
    If matches.Count > 0 Then
        GetPrint = matches(0).SubMatches(0)
    Else
        GetPrint = ""
    End If
End Function
WScript.Echo GetPrint(WScript.Arguments(0))