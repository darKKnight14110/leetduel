param(
    [string]$Ref = "v0.3.1",
    [string]$DatasetCommit = "182cd19fd53161efd70a1ef074fe1056b659bfa3",
    [ValidateSet("train", "test")]
    [string]$Split = "train",
    [string]$ProblemServiceUrl = "http://localhost:8085",
    [string]$RejectReport = "reports/leetcode-dataset-rejects.json",
    [int]$MaxRecords = 0
)

$ErrorActionPreference = "Stop"
$source = "newfacade/LeetCodeDataset@$Ref"
$headers = @{ "User-Agent" = "LeetDuel-practice-import" }

function Get-PropertyValue {
    param([object]$Object, [string[]]$Names)
    foreach ($name in $Names) {
        $property = $Object.PSObject.Properties[$name]
        if ($null -ne $property -and $null -ne $property.Value) { return $property.Value }
    }
    return $null
}

function Skip-Whitespace {
    param([hashtable]$State)
    while ($State.Index -lt $State.Text.Length -and [char]::IsWhiteSpace($State.Text[$State.Index])) {
        $State.Index++
    }
}

function Parse-PythonString {
    param([hashtable]$State)
    $quote = $State.Text[$State.Index]
    $State.Index++
    $builder = [Text.StringBuilder]::new()
    while ($State.Index -lt $State.Text.Length) {
        $character = $State.Text[$State.Index]
        $State.Index++
        if ($character -eq $quote) { return $builder.ToString() }
        if ($character -ne '\') {
            [void]$builder.Append($character)
            continue
        }
        if ($State.Index -ge $State.Text.Length) { throw "unterminated string escape" }
        $escaped = $State.Text[$State.Index]
        $State.Index++
        switch ($escaped) {
            'n' { [void]$builder.Append("`n") }
            'r' { [void]$builder.Append("`r") }
            't' { [void]$builder.Append("`t") }
            'b' { [void]$builder.Append("`b") }
            'f' { [void]$builder.Append("`f") }
            default { [void]$builder.Append($escaped) }
        }
    }
    throw "unterminated string"
}

function Parse-PythonValue {
    param([hashtable]$State)
    Skip-Whitespace $State
    if ($State.Index -ge $State.Text.Length) { throw "missing value" }
    $character = $State.Text[$State.Index]

    if ($character -eq "'" -or $character -eq '"') {
        $value = Parse-PythonString $State
        Write-Output -NoEnumerate $value
        return
    }

    if ($character -eq '[' -or $character -eq '(') {
        $closing = if ($character -eq '[') { ']' } else { ')' }
        $State.Index++
        $values = [System.Collections.Generic.List[object]]::new()
        while ($true) {
            Skip-Whitespace $State
            if ($State.Index -lt $State.Text.Length -and $State.Text[$State.Index] -eq $closing) {
                $State.Index++
                Write-Output -NoEnumerate ([object[]]$values.ToArray())
                return
            }
            $values.Add((Parse-PythonValue $State))
            Skip-Whitespace $State
            if ($State.Index -lt $State.Text.Length -and $State.Text[$State.Index] -eq ',') {
                $State.Index++
                continue
            }
            if ($State.Index -lt $State.Text.Length -and $State.Text[$State.Index] -eq $closing) {
                $State.Index++
                Write-Output -NoEnumerate ([object[]]$values.ToArray())
                return
            }
            throw "expected '$closing'"
        }
    }

    if ($character -eq '{') {
        $State.Index++
        $values = @{}
        while ($true) {
            Skip-Whitespace $State
            if ($State.Index -lt $State.Text.Length -and $State.Text[$State.Index] -eq '}') {
                $State.Index++
                Write-Output -NoEnumerate $values
                return
            }
            $key = [string](Parse-PythonValue $State)
            Skip-Whitespace $State
            if ($State.Index -ge $State.Text.Length -or $State.Text[$State.Index] -ne ':') { throw "expected ':'" }
            $State.Index++
            $values[$key] = Parse-PythonValue $State
            Skip-Whitespace $State
            if ($State.Index -lt $State.Text.Length -and $State.Text[$State.Index] -eq ',') {
                $State.Index++
                continue
            }
            if ($State.Index -lt $State.Text.Length -and $State.Text[$State.Index] -eq '}') {
                $State.Index++
                Write-Output -NoEnumerate $values
                return
            }
            throw "expected '}'"
        }
    }

    $numberMatch = [regex]::Match($State.Text.Substring($State.Index), '^-?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?')
    if ($numberMatch.Success) {
        $token = $numberMatch.Value
        $State.Index += $token.Length
        if ($token -match '[.eE]') {
            Write-Output -NoEnumerate ([double]::Parse($token, [Globalization.CultureInfo]::InvariantCulture))
        } else {
            Write-Output -NoEnumerate ([long]::Parse($token, [Globalization.CultureInfo]::InvariantCulture))
        }
        return
    }

    $identifierMatch = [regex]::Match($State.Text.Substring($State.Index), '^[A-Za-z_][A-Za-z0-9_]*')
    if ($identifierMatch.Success) {
        $token = $identifierMatch.Value
        $State.Index += $token.Length
        switch ($token) {
            'None' { Write-Output -NoEnumerate $null; return }
            'null' { Write-Output -NoEnumerate $null; return }
            'True' { Write-Output -NoEnumerate $true; return }
            'False' { Write-Output -NoEnumerate $false; return }
            default { throw "unsupported literal '$token'" }
        }
    }
    throw "unsupported literal at position $($State.Index)"
}

function Convert-PythonLiteralToJson {
    param([string]$Text, [switch]$AllowBareString)
    $state = @{ Text = $Text.Trim(); Index = 0 }
    try {
        $value = Parse-PythonValue $state
        Skip-Whitespace $state
        if ($state.Index -ne $state.Text.Length) { throw "trailing input" }
        return ($value | ConvertTo-Json -Compress -Depth 20)
    } catch {
        if ($AllowBareString) { return ($Text.Trim() | ConvertTo-Json -Compress) }
        throw $_
    }
}

function Split-TopLevel {
    param([string]$Text)
    $parts = [System.Collections.Generic.List[string]]::new()
    $start = 0
    $depth = 0
    $quote = $null
    $escaped = $false
    for ($index = 0; $index -lt $Text.Length; $index++) {
        $character = $Text[$index]
        if ($null -ne $quote) {
            if ($escaped) { $escaped = $false }
            elseif ($character -eq '\') { $escaped = $true }
            elseif ($character -eq $quote) { $quote = $null }
            continue
        }
        if ($character -eq "'" -or $character -eq '"') { $quote = $character; continue }
        if ($character -eq '[' -or $character -eq '(' -or $character -eq '{') { $depth++; continue }
        if ($character -eq ']' -or $character -eq ')' -or $character -eq '}') { $depth--; continue }
        if ($character -eq ',' -and $depth -eq 0) {
            $parts.Add($Text.Substring($start, $index - $start).Trim())
            $start = $index + 1
        }
    }
    $parts.Add($Text.Substring($start).Trim())
    return $parts.ToArray()
}

function Find-TopLevelEquals {
    param([string]$Text)
    $depth = 0
    $quote = $null
    $escaped = $false
    for ($index = 0; $index -lt $Text.Length; $index++) {
        $character = $Text[$index]
        if ($null -ne $quote) {
            if ($escaped) { $escaped = $false }
            elseif ($character -eq '\') { $escaped = $true }
            elseif ($character -eq $quote) { $quote = $null }
            continue
        }
        if ($character -eq "'" -or $character -eq '"') { $quote = $character; continue }
        if ($character -eq '[' -or $character -eq '(' -or $character -eq '{') { $depth++; continue }
        if ($character -eq ']' -or $character -eq ')' -or $character -eq '}') { $depth--; continue }
        if ($character -eq '=' -and $depth -eq 0) { return $index }
    }
    return -1
}

function Get-ParameterNames {
    param([string]$Stub, [string]$FunctionName)
    $match = [regex]::Match($Stub, 'def\s+' + [regex]::Escape($FunctionName) + '\s*\(([^)]*)\)')
    if (-not $match.Success) { throw "no supported Python function signature for $FunctionName" }
    return @($match.Groups[1].Value -split ',' | ForEach-Object {
        $name = ($_ -split ':|=')[0].Trim()
        if ($name -and $name -ne 'self') { $name }
    })
}

function Convert-InputToJson {
    param([string]$InputText, [string[]]$ParameterNames)
    $trimmed = $InputText.Trim()
    $parts = @(Split-TopLevel $trimmed)
    if ($parts.Count -eq 1 -and (Find-TopLevelEquals $parts[0]) -lt 0) {
        return Convert-PythonLiteralToJson $trimmed
    }
    $byName = @{}
    foreach ($part in $parts) {
        $equals = Find-TopLevelEquals $part
        if ($equals -lt 1) { throw "input is not named function arguments" }
        $name = $part.Substring(0, $equals).Trim()
        $byName[$name] = $part.Substring($equals + 1).Trim()
    }
    $arguments = [System.Collections.Generic.List[object]]::new()
    foreach ($name in $ParameterNames) {
        if (-not $byName.ContainsKey($name)) { throw "input is missing parameter '$name'" }
        $state = @{ Text = $byName[$name]; Index = 0 }
        $arguments.Add((Parse-PythonValue $state))
        Skip-Whitespace $state
        if ($state.Index -ne $state.Text.Length) { throw "input parameter '$name' has trailing data" }
    }
    return ($arguments.ToArray() | ConvertTo-Json -Compress -Depth 20)
}

function Get-TestCases {
    param([object]$Record)
    $io = Get-PropertyValue $Record @("input_output", "inputOutput")
    $cases = [System.Collections.Generic.List[object]]::new()
    foreach ($item in @($io)) {
        $input = Get-PropertyValue $item @("input", "inputs", "args")
        $output = Get-PropertyValue $item @("output", "expected_output", "expectedOutput")
        if ($null -ne $input -and $null -ne $output) {
            $cases.Add(@{ input = [string]$input; expectedOutput = [string]$output; isSample = ($cases.Count -lt 2) })
        }
    }
    return $cases.ToArray()
}

function Convert-Record {
    param([object]$Record)
    $taskId = [string](Get-PropertyValue $Record @("task_id", "question_id", "id"))
    $title = Get-PropertyValue $Record @("title", "question_title", "name")
    if ([string]::IsNullOrWhiteSpace([string]$title)) {
        $title = [Globalization.CultureInfo]::InvariantCulture.TextInfo.ToTitleCase(($taskId -replace '[-_]+', ' '))
    }
    $description = Get-PropertyValue $Record @("problem_description", "description", "question")
    $difficulty = Get-PropertyValue $Record @("difficulty", "level")
    $entryPoint = [string](Get-PropertyValue $Record @("entry_point", "function_name", "functionName"))
    $starter = [string](Get-PropertyValue $Record @("starter_code", "starterCode", "code"))
    if ([string]::IsNullOrWhiteSpace($taskId) -or [string]::IsNullOrWhiteSpace([string]$description)) { throw "missing task id or description" }
    if ([string]::IsNullOrWhiteSpace($starter)) { throw "no Python starter code" }
    $functionMatch = [regex]::Match($entryPoint, '(?:^|\.)([A-Za-z_]\w*)$')
    if (-not $functionMatch.Success) { throw "no supported function entry point" }
    $functionName = $functionMatch.Groups[1].Value
    $parameterNames = @(Get-ParameterNames $starter $functionName)
    if ($parameterNames.Count -eq 0) { throw "function has no supported parameters" }

    $rawCases = @(Get-TestCases $Record)
    if ($rawCases.Count -lt 1) { throw "no compatible input/output test cases" }
    $cases = [System.Collections.Generic.List[object]]::new()
    foreach ($rawCase in $rawCases) {
        $inputJson = Convert-InputToJson $rawCase.input $parameterNames
        $outputJson = Convert-PythonLiteralToJson $rawCase.expectedOutput -AllowBareString
        $cases.Add(@{ input = $inputJson; expectedOutput = $outputJson; isSample = $rawCase.isSample })
    }

    $firstInput = $cases[0].input | ConvertFrom-Json
    $firstOutput = $cases[0].expectedOutput | ConvertFrom-Json
    if ($firstInput -isnot [System.Array] -or $firstInput.Count -ne $parameterNames.Count) { throw "function harness requires one argument array" }
    $parameterTypes = @($firstInput | ForEach-Object { Get-TypeName $_ })
    $returnType = Get-TypeName $firstOutput
    if ($parameterTypes -contains $null -or $null -eq $returnType) { throw "uses an unsupported harness type" }

    $normalizedDifficulty = ([string]$difficulty).ToUpperInvariant()
    if ($normalizedDifficulty -notin @("EASY", "MEDIUM", "HARD")) { throw "unsupported difficulty" }
    $tagsValue = Get-PropertyValue $Record @("tags", "topics")
    $tags = if ($tagsValue -is [string]) { @($tagsValue -split '[,;]' | ForEach-Object { $_.Trim() } | Where-Object { $_ }) } else { @($tagsValue | ForEach-Object { [string]$_ }) }
    $slug = ($taskId.ToLowerInvariant() -replace '[^a-z0-9]+', '-').Trim('-')
    if ([string]::IsNullOrWhiteSpace($slug)) { throw "task id did not produce a usable slug" }
    $slug = $slug.Substring(0, [Math]::Min(80, $slug.Length))
    $stub = "def $functionName(" + ($parameterNames -join ', ') + "):`n    pass"
    $parameters = @(); for ($index = 0; $index -lt $parameterNames.Count; $index++) { $parameters += @{ name = $parameterNames[$index]; type = $parameterTypes[$index] } }
    $problem = @{ slug = $slug; title = [string]$title; description = [string]$description; difficulty = $normalizedDifficulty; tags = $tags; functionName = $functionName; returnType = $returnType; parameters = $parameters; languageStubs = @{ PYTHON = $stub }; testCases = $cases.ToArray() }
    return @{ source = $source; sourceId = $taskId; problem = $problem }
}

function Get-TypeName {
    param([object]$Value)
    if ($null -eq $Value) { return "string" }
    if ($Value -is [bool]) { return "boolean" }
    if ($Value -is [int] -or $Value -is [long]) { return "int" }
    if ($Value -is [double] -or $Value -is [decimal]) { return "double" }
    if ($Value -is [string]) { return "string" }
    if ($Value -is [System.Array]) {
        if ($Value.Count -eq 0) { return "string[]" }
        $nested = Get-TypeName $Value[0]
        if ($nested -match '^(int|long|double|boolean|string)(\[\])*') { return "$nested[]" }
    }
    return $null
}

$downloadPath = Join-Path ([IO.Path]::GetTempPath()) ("leetduel-dataset-" + [guid]::NewGuid() + ".jsonl.gz")
$downloadUrl = "https://raw.githubusercontent.com/newfacade/LeetCodeDataset/$DatasetCommit/data/LeetCodeDataset-$Ref-$Split.jsonl.gz"
$rejects = [System.Collections.Generic.List[object]]::new()
$imported = 0
$processed = 0
$reader = $null
$gzip = $null
$file = $null
try {
    Invoke-WebRequest -Headers $headers -Uri $downloadUrl -OutFile $downloadPath
    $file = [IO.File]::OpenRead($downloadPath)
    $gzip = [IO.Compression.GzipStream]::new($file, [IO.Compression.CompressionMode]::Decompress)
    $reader = [IO.StreamReader]::new($gzip)
    while ($null -ne ($line = $reader.ReadLine())) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        if ($MaxRecords -gt 0 -and $processed -ge $MaxRecords) { break }
        $processed++
        $record = $line | ConvertFrom-Json
        try {
            $payload = Convert-Record $record
            Invoke-RestMethod -Method Post -Uri "$ProblemServiceUrl/internal/problems/import" -ContentType "application/json" -Body ($payload | ConvertTo-Json -Depth 30) | Out-Null
            $imported++
        } catch {
            $rejects.Add(@{ sourceId = [string](Get-PropertyValue $record @("task_id", "question_id", "id")); reason = $_.Exception.Message })
        }
    }
} finally {
    if ($null -ne $reader) { $reader.Dispose() }
    if ($null -ne $gzip) { $gzip.Dispose() }
    if ($null -ne $file) { $file.Dispose() }
    if (Test-Path -LiteralPath $downloadPath) { Remove-Item -LiteralPath $downloadPath -Force }
}

$reportPath = Join-Path (Get-Location) $RejectReport
$reportDirectory = Split-Path $reportPath -Parent
if (-not [string]::IsNullOrWhiteSpace($reportDirectory)) { New-Item -ItemType Directory -Force -Path $reportDirectory | Out-Null }
@{ source = $source; datasetCommit = $DatasetCommit; split = $Split; processed = $processed; imported = $imported; rejected = $rejects.Count; rejects = $rejects.ToArray() } | ConvertTo-Json -Depth 20 | Set-Content -Encoding UTF8 $reportPath
Write-Host "Imported $imported compatible problems from $source; rejected $($rejects.Count)."
