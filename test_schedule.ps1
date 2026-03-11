# ============================================================
# ARARE Timetable End-to-End Test Script
# Usage: powershell.exe -ExecutionPolicy Bypass -File test_schedule.ps1
# ============================================================
param()
$BASE = "http://localhost:8080/api/v1"
$ErrorActionPreference = "Stop"

$script:PASS = 0
$script:FAIL = 0
$script:ERRORS = [System.Collections.Generic.List[string]]::new()

function Write-Ok   { param($m) Write-Host "  [PASS] $m" -ForegroundColor Green;  $script:PASS++ }
function Write-Fail { param($m) Write-Host "  [FAIL] $m" -ForegroundColor Red;   $script:FAIL++; $script:ERRORS.Add($m) }
function Write-Info { param($m) Write-Host "`n>>> $m" -ForegroundColor Cyan }
function Write-Warn { param($m) Write-Host "  [WARN] $m" -ForegroundColor Yellow }

function Invoke-Post { param($path, $body)
    Invoke-RestMethod -Uri "$BASE$path" -Method POST `
        -ContentType "application/json" `
        -Body ($body | ConvertTo-Json -Depth 6 -Compress)
}
function Invoke-Put { param($path, $body)
    Invoke-RestMethod -Uri "$BASE$path" -Method PUT `
        -ContentType "application/json" `
        -Body ($body | ConvertTo-Json -Depth 6 -Compress)
}
function Invoke-Get { param($path)
    Invoke-RestMethod -Uri "$BASE$path" -Method GET
}

# ---------------------------------------------------------------
# 0. Wait for the app
# ---------------------------------------------------------------
Write-Info "Waiting for Spring Boot app to be ready..."
$ready = $false
for ($i = 1; $i -le 40; $i++) {
    try {
        $null = Invoke-Get "/buildings"
        $ready = $true
        Write-Ok "App is up (attempt $i)"
        break
    } catch {
        Write-Host "  Attempt $i/40 - not ready yet, retrying in 3s..." -ForegroundColor DarkGray
        Start-Sleep 3
    }
}
if (-not $ready) { Write-Fail "App never became ready - aborting"; exit 1 }

# ---------------------------------------------------------------
# 1. Building
# ---------------------------------------------------------------
Write-Info "Step 1: Building"
$buildings = Invoke-Get "/buildings"
if ($buildings.Count -gt 0) {
    $bld = $buildings[0]
    Write-Warn "Reusing building '$($bld.name)' id=$($bld.id)"
} else {
    $bld = Invoke-Post "/buildings" @{ name="Main Block"; location="Campus A" }
    Write-Ok "Created building '$($bld.name)' id=$($bld.id)"
}

# ---------------------------------------------------------------
# 2. Department
# ---------------------------------------------------------------
Write-Info "Step 2: Department"
$depts = Invoke-Get "/departments"
if ($depts.Count -gt 0) {
    $dept = $depts[0]
    Write-Warn "Reusing department '$($dept.name)' id=$($dept.id)"
} else {
    $dept = Invoke-Post "/departments" @{
        name = "Computer Science"
        code = "CSE"
        buildingIds = @($bld.id)
    }
    Write-Ok "Created department '$($dept.name)' id=$($dept.id)"
}

# ---------------------------------------------------------------
# 3. Timeslots - Regular shift Mon-Sat (7 CLASS + 1 BREAK per day)
# ---------------------------------------------------------------
Write-Info "Step 3: Timeslots (Regular shift Mon-Sat)"
$existingSlots = Invoke-Get "/timeslots"
$days = @("MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY")

# Definition: each slot as PSCustomObject so we can property-access cleanly
$slotDefs = @(
    [PSCustomObject]@{ s="09:30:00"; e="10:20:00"; t="CLASS" },
    [PSCustomObject]@{ s="10:20:00"; e="11:10:00"; t="CLASS" },
    [PSCustomObject]@{ s="11:10:00"; e="12:00:00"; t="CLASS" },
    [PSCustomObject]@{ s="12:00:00"; e="12:40:00"; t="BREAK" },
    [PSCustomObject]@{ s="12:40:00"; e="13:30:00"; t="CLASS" },
    [PSCustomObject]@{ s="13:30:00"; e="14:20:00"; t="CLASS" },
    [PSCustomObject]@{ s="14:20:00"; e="15:10:00"; t="CLASS" },
    [PSCustomObject]@{ s="15:10:00"; e="16:20:00"; t="CLASS" }
)

# Build lookup by day+startTime
$slotLookup = @{}
foreach ($ex in $existingSlots) {
    $startStr = $ex.startTime.ToString()
    $k = "$($ex.day)___$startStr"
    $slotLookup[$k] = $ex
}

$classSlotIds = [System.Collections.Generic.List[long]]::new()
$createdNew = 0
foreach ($day in $days) {
    foreach ($def in $slotDefs) {
        $k = "$($day)___$($def.s)"
        if ($slotLookup.ContainsKey($k)) {
            $ts = $slotLookup[$k]
        } else {
            $ts = Invoke-Post "/timeslots" @{
                day = $day
                startTime = $def.s
                endTime   = $def.e
                type      = $def.t
            }
            $createdNew++
        }
        if ($def.t -eq "CLASS") { $classSlotIds.Add([long]$ts.id) }
    }
}
Write-Ok "Timeslots ready: $($classSlotIds.Count) CLASS slots across 6 days ($createdNew new)"

# ---------------------------------------------------------------
# 4. Rooms
# ---------------------------------------------------------------
Write-Info "Step 4: Rooms"
$allRooms = Invoke-Get "/rooms"
$lectureRoom = $allRooms | Where-Object { $_.type -eq "LECTURE" } | Select-Object -First 1
$labRoom     = $allRooms | Where-Object { $_.type -eq "LAB" } | Select-Object -First 1

if ($null -eq $lectureRoom) {
    $lectureRoom = Invoke-Post "/rooms" @{
        buildingId = $bld.id
        roomNumber = "101"
        type       = "LECTURE"
        capacity   = 70
        availableTimeslotIds = @()
    }
    Write-Ok "Created Lecture Room 101 (cap 70) id=$($lectureRoom.id)"
} else {
    Write-Warn "Reusing lecture room '$($lectureRoom.roomNumber)' id=$($lectureRoom.id)"
}

if ($null -eq $labRoom) {
    $labRoom = Invoke-Post "/rooms" @{
        buildingId    = $bld.id
        roomNumber    = "Lab-201"
        type          = "LAB"
        labSubtype    = "COMPUTER_LAB"
        capacity      = 35
        availableTimeslotIds = @()
    }
    Write-Ok "Created Computer Lab-201 (cap 35) id=$($labRoom.id)"
} else {
    Write-Warn "Reusing lab room '$($labRoom.roomNumber)' id=$($labRoom.id)"
}

# ---------------------------------------------------------------
# 5. Subjects
# ---------------------------------------------------------------
Write-Info "Step 5: Subjects"
$allSubjects = Invoke-Get "/subjects"

function Get-OrCreate-Subject {
    param($name, $code, $weekly, $chunk, $isLab, $roomType, $labSub)
    $ex = $allSubjects | Where-Object { $_.code -eq $code } | Select-Object -First 1
    if ($null -ne $ex) { Write-Warn "Reusing subject '$code' id=$($ex.id)"; return $ex }
    $body = @{
        name                  = $name
        code                  = $code
        departmentId          = $dept.id
        weeklyHours           = $weekly
        chunkHours            = $chunk
        roomTypeRequired      = $roomType
        isLab                 = $isLab
        requiresTeacher       = $true
        requiresRoom          = $true
        minGapBetweenSessions = 0
        maxSessionsPerDay     = 2
    }
    if ($null -ne $labSub) { $body.labSubtypeRequired = $labSub }
    $s = Invoke-Post "/subjects" $body
    Write-Ok "Created subject '$code' id=$($s.id)"
    return $s
}

$subDS   = Get-OrCreate-Subject "Data Structures"    "CS301" 4 1 $false "LECTURE" $null
$subMath = Get-OrCreate-Subject "Engineering Math"   "MA101" 4 1 $false "LECTURE" $null
$subOS   = Get-OrCreate-Subject "Operating Systems"  "CS302" 3 1 $false "LECTURE" $null
$subEng  = Get-OrCreate-Subject "English"            "HU101" 2 1 $false "LECTURE" $null
$subLab  = Get-OrCreate-Subject "Computer Lab"       "CS311" 2 2 $true  "LAB"     "COMPUTER_LAB"

# ---------------------------------------------------------------
# 6. Teachers
# ---------------------------------------------------------------
Write-Info "Step 6: Teachers"
$allTeachers = Invoke-Get "/teachers"

function Get-OrCreate-Teacher {
    param($name, $subIds)
    $ex = $allTeachers | Where-Object { $_.name -eq $name } | Select-Object -First 1
    $body = @{
        name                  = $name
        subjectIds            = $subIds
        availableTimeslotIds  = @()
        preferredBuildingIds  = @($bld.id)
        maxDailyHours         = 6
        maxWeeklyHours        = 24
        maxConsecutiveClasses = 3
        movementPenalty       = 1
    }
    if ($null -ne $ex) {
        $t = Invoke-Put "/teachers/$($ex.id)" $body
        Write-Warn "Updated teacher '$name' id=$($t.id)"
        return $t
    }
    $t = Invoke-Post "/teachers" $body
    Write-Ok "Created teacher '$name' id=$($t.id)"
    return $t
}

$t1 = Get-OrCreate-Teacher "Dr. A. Kumar"  @($subDS.id,  $subOS.id,  $subLab.id)
$t2 = Get-OrCreate-Teacher "Dr. B. Sharma" @($subMath.id,$subEng.id)

# ---------------------------------------------------------------
# 7. Batches
# ---------------------------------------------------------------
Write-Info "Step 7: Batches"
$allBatches = Invoke-Get "/batches"

function Get-OrCreate-Batch {
    param($yr, $sec)
    $ex = $allBatches | Where-Object { $_.year -eq $yr -and $_.section -eq $sec -and $_.departmentId -eq $dept.id } | Select-Object -First 1
    if ($null -ne $ex) { Write-Warn "Reusing batch Y$yr-$sec id=$($ex.id)"; return $ex }
    $b = Invoke-Post "/batches" @{
        departmentId     = $dept.id
        year             = $yr
        section          = $sec
        studentCount     = 60
        workingDays      = @("MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY")
        preferredFreeDay = "SATURDAY"
    }
    Write-Ok "Created batch Y$yr-$sec id=$($b.id)"
    return $b
}

$batchA = Get-OrCreate-Batch 1 "A"
$batchB = Get-OrCreate-Batch 1 "B"

# ---------------------------------------------------------------
# 8. Class Sections (for Lab splits)
# ---------------------------------------------------------------
Write-Info "Step 8: Class Sections (lab sub-groups)"

function Get-OrCreate-Section {
    param($batchId, $label, $sz)
    $secList = Invoke-Get "/class-sections/batch/$batchId"
    $ex = $secList | Where-Object { $_.label -eq $label } | Select-Object -First 1
    if ($null -ne $ex) { Write-Warn "Reusing section '$label' id=$($ex.id)"; return $ex }
    $s = Invoke-Post "/class-sections" @{ batchId=$batchId; label=$label; size=$sz }
    Write-Ok "Created section '$label' id=$($s.id)"
    return $s
}

$secA1 = Get-OrCreate-Section $batchA.id "A1" 30
$secA2 = Get-OrCreate-Section $batchA.id "A2" 30
$secB1 = Get-OrCreate-Section $batchB.id "B1" 28
$secB2 = Get-OrCreate-Section $batchB.id "B2" 27

# ---------------------------------------------------------------
# 9. Pre-flight summary
# ---------------------------------------------------------------
Write-Info "Step 9: Pre-flight Summary"
Write-Host ""
Write-Host "  Building   : $($bld.name) (id=$($bld.id))" -ForegroundColor White
Write-Host "  Department : $($dept.name) (id=$($dept.id))" -ForegroundColor White
Write-Host "  CLASS slots: $($classSlotIds.Count) (7 per day x 6 days)" -ForegroundColor White
Write-Host "  Rooms      : Lecture=$($lectureRoom.id)  Lab=$($labRoom.id)" -ForegroundColor White
Write-Host "  Subjects   : DS=$($subDS.id) Math=$($subMath.id) OS=$($subOS.id) Eng=$($subEng.id) Lab=$($subLab.id)" -ForegroundColor White
Write-Host "  Teachers   : T1=$($t1.id) T2=$($t2.id)" -ForegroundColor White
Write-Host "  Batches    : A=$($batchA.id)  B=$($batchB.id)" -ForegroundColor White
Write-Host "  Sections   : A1=$($secA1.id) A2=$($secA2.id) B1=$($secB1.id) B2=$($secB2.id)" -ForegroundColor White

# Expected sessions per batch: DS=4, Math=4, OS=3, Eng=2 -> 13 lecture sessions
# Lab: 2/2=1 occurrence x 2 sections per batch = 2 lab sessions per batch
# Total per batch: 13 + 2 = 15; x2 batches = 30 total
$expectedSessions = (4 + 4 + 3 + 2) * 2 + 1 * 4
Write-Host "  Expected sessions: $expectedSessions (lecture+lab across 2 batches)" -ForegroundColor White

# ---------------------------------------------------------------
# 10. Generate Schedule
# ---------------------------------------------------------------
Write-Info "Step 10: Generate Schedule  (solver runs for 30s...)"
try {
    $sched = Invoke-Post "/schedules/generate" @{
        name         = "CSE Test $(Get-Date -Format 'HH:mm:ss')"
        scope        = "DEPARTMENT"
        departmentId = $dept.id
    }
    Write-Ok "Schedule accepted: id=$($sched.id) status=$($sched.status) score='$($sched.score)'"
} catch {
    Write-Fail "Schedule generation request failed: $($_.Exception.Message)"
    exit 1
}
$schedId = $sched.id

# ---------------------------------------------------------------
# 11. Fetch sessions
# ---------------------------------------------------------------
Write-Info "Step 11: Fetching sessions"
$sessions = Invoke-Get "/schedules/$schedId/sessions"
if ($null -eq $sessions) { $sessions = @() }
Write-Ok "Total sessions returned: $($sessions.Count)"

if ($sessions.Count -eq 0) {
    Write-Fail "No sessions generated - nothing to validate"
    exit 1
}

# ---------------------------------------------------------------
# 12. Check all planning variables are assigned
# ---------------------------------------------------------------
Write-Info "Step 12: All planning variables assigned?"

$noTeacher  = @($sessions | Where-Object { $null -eq $_.teacherId  -or $_.teacherId  -eq 0 })
$noRoom     = @($sessions | Where-Object { $null -eq $_.roomId     -or $_.roomId     -eq 0 })
$noTimeslot = @($sessions | Where-Object { $null -eq $_.timeslotId -or $_.timeslotId -eq 0 })

if ($noTeacher.Count -eq 0) {
    Write-Ok "All $($sessions.Count) sessions have a teacher"
} else {
    Write-Fail "$($noTeacher.Count)/$($sessions.Count) sessions missing teacher"
    $noTeacher | Select-Object id,subjectName,batchId,sectionId | Format-Table | Out-String | ForEach-Object { Write-Host $_ -ForegroundColor Red }
}
if ($noRoom.Count -eq 0) {
    Write-Ok "All sessions have a room"
} else {
    Write-Fail "$($noRoom.Count) sessions missing room"
    $noRoom | Select-Object id,subjectName | Format-Table | Out-String | ForEach-Object { Write-Host $_ -ForegroundColor Red }
}
if ($noTimeslot.Count -eq 0) {
    Write-Ok "All sessions have a timeslot"
} else {
    Write-Fail "$($noTimeslot.Count) sessions missing timeslot"
}

# ---------------------------------------------------------------
# 13. Hard constraint: no teacher double-booked
# ---------------------------------------------------------------
Write-Info "Step 13: No teacher double-booking"
$teacherConflicts = 0
$seen = @{}
foreach ($s in $sessions) {
    if ($null -eq $s.timeslotId -or $null -eq $s.teacherId) { continue }
    $k = "T$($s.teacherId)_TS$($s.timeslotId)"
    if ($seen.ContainsKey($k)) {
        $teacherConflicts++
        Write-Host "  CONFLICT: Teacher $($s.teacherName) double-booked at slot $($s.timeslotId): '$($s.subjectName)' vs '$($seen[$k])'" -ForegroundColor Red
    } else { $seen[$k] = $s.subjectName }
}
if ($teacherConflicts -eq 0) { Write-Ok "No teacher double-booking" }
else { Write-Fail "$teacherConflicts teacher double-booking conflict(s)" }

# ---------------------------------------------------------------
# 14. Hard constraint: no room double-booked
# ---------------------------------------------------------------
Write-Info "Step 14: No room double-booking"
$roomConflicts = 0
$seen2 = @{}
foreach ($s in $sessions) {
    if ($null -eq $s.timeslotId -or $null -eq $s.roomId) { continue }
    $k = "R$($s.roomId)_TS$($s.timeslotId)"
    if ($seen2.ContainsKey($k)) {
        $roomConflicts++
        Write-Host "  CONFLICT: Room $($s.roomNumber) double-booked at slot $($s.timeslotId): '$($s.subjectName)' vs '$($seen2[$k])'" -ForegroundColor Red
    } else { $seen2[$k] = $s.subjectName }
}
if ($roomConflicts -eq 0) { Write-Ok "No room double-booking" }
else { Write-Fail "$roomConflicts room double-booking conflict(s)" }

# ---------------------------------------------------------------
# 15. Hard constraint: no batch double-booked
# ---------------------------------------------------------------
Write-Info "Step 15: No batch double-booking"
$batchConflicts = 0
$seen3 = @{}
foreach ($s in $sessions) {
    if ($null -eq $s.timeslotId -or $null -eq $s.batchId) { continue }
    $k = "B$($s.batchId)_TS$($s.timeslotId)"
    if ($seen3.ContainsKey($k)) {
        $batchConflicts++
        Write-Host "  CONFLICT: Batch $($s.batchId) double-scheduled at slot $($s.timeslotId): '$($s.subjectName)' vs '$($seen3[$k])'" -ForegroundColor Red
    } else { $seen3[$k] = $s.subjectName }
}
if ($batchConflicts -eq 0) { Write-Ok "No batch double-booking" }
else { Write-Fail "$batchConflicts batch double-booking conflict(s)" }

# ---------------------------------------------------------------
# 16. Hard constraint: lab sessions use LAB rooms
# ---------------------------------------------------------------
Write-Info "Step 16: Lab sessions in LAB rooms"
$allRoomsNow = Invoke-Get "/rooms"
$labRoomIds  = @($allRoomsNow | Where-Object { $_.type -eq "LAB" } | Select-Object -ExpandProperty id)
$labSubIds   = @($subLab.id)

$labSessions = @($sessions | Where-Object { $labSubIds -contains $_.subjectId })
$badLabRooms = @($labSessions | Where-Object { $labRoomIds -notcontains $_.roomId })

if ($labSessions.Count -gt 0) {
    Write-Host "  Lab sessions total: $($labSessions.Count)" -ForegroundColor White
}
if ($badLabRooms.Count -eq 0) {
    Write-Ok "All lab sessions placed in LAB rooms"
} else {
    Write-Fail "$($badLabRooms.Count) lab session(s) placed in non-LAB room"
    $badLabRooms | ForEach-Object { Write-Host "    Session $($_.id) in room $($_.roomId)" -ForegroundColor Red }
}

# ---------------------------------------------------------------
# 17. Score via API
# ---------------------------------------------------------------
Write-Info "Step 17: Score Explanation"
try {
    $score = Invoke-Get "/schedules/$schedId/score-explanation"
    $scoreColor = if ($score.hardScore -eq 0) { "Green" } else { "Red" }
    Write-Host ""
    Write-Host "  Score    : $($score.score)" -ForegroundColor $scoreColor
    Write-Host "  Feasible : $($score.feasible)" -ForegroundColor $scoreColor
    Write-Host "  Hard     : $($score.hardScore)" -ForegroundColor $(if ($score.hardScore -eq 0) {"Green"} else {"Red"})
    Write-Host "  Medium   : $($score.mediumScore)" -ForegroundColor Yellow
    Write-Host "  Soft     : $($score.softScore)" -ForegroundColor Gray
    Write-Host ""
    if ($score.constraints.Count -gt 0) {
        Write-Host "  Constraint Breakdown:" -ForegroundColor White
        foreach ($c in ($score.constraints | Sort-Object level)) {
            $col = if ($c.level -eq "HARD") {"Red"} elseif ($c.level -eq "MEDIUM") {"Yellow"} else {"DarkGray"}
            Write-Host ("    [{0,-6}] {1,-45} {2,3} violations  {3}" -f $c.level, $c.constraintName, $c.matchCount, $c.scoreImpact) -ForegroundColor $col
        }
    }
    Write-Host ""
    if ($score.hardScore -eq 0) { Write-Ok "FEASIBLE - zero hard-constraint violations" }
    else { Write-Fail "INFEASIBLE - $($score.hardScore) hard violation(s) found" }
} catch {
    Write-Fail "Score explanation fetch failed: $($_.Exception.Message)"
}

# ---------------------------------------------------------------
# 18. Print the final timetable
# ---------------------------------------------------------------
Write-Info "Step 18: Generated Timetable"
$allSlots = Invoke-Get "/timeslots"
$tsMap = @{}; $allSlots | ForEach-Object { $tsMap[[long]$_.id] = $_ }

$daysOrder = @("MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY")
foreach ($day in $daysOrder) {
    $daySessions = @($sessions | Where-Object {
        $null -ne $_.timeslotId -and $tsMap.ContainsKey([long]$_.timeslotId) -and $tsMap[[long]$_.timeslotId].day -eq $day
    })
    if ($daySessions.Count -eq 0) { continue }
    $sorted = $daySessions | Sort-Object { $tsMap[[long]$_.timeslotId].startTime }
    Write-Host ""
    Write-Host "  ---- $day ----" -ForegroundColor Magenta
    foreach ($s in $sorted) {
        $ts = $tsMap[[long]$s.timeslotId]
        $start5 = $ts.startTime.ToString().SubString(0, [Math]::Min(5,$ts.startTime.ToString().Length))
        $end5   = $ts.endTime.ToString().SubString(0, [Math]::Min(5,$ts.endTime.ToString().Length))
        $who = if ($s.batchId) { "Batch $($s.batchId)" } else { "Sec $($s.sectionId)" }
        Write-Host ("    {0,-11} | {1,-22} | {2,-12} | Room:{3,-8} | {4}" -f "$start5-$end5", $s.subjectName, $who, $s.roomNumber, $s.teacherName) -ForegroundColor White
    }
}

# ---------------------------------------------------------------
# Summary
# ---------------------------------------------------------------
Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host " TEST SUMMARY" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host " Passed : $($script:PASS)" -ForegroundColor Green
$fc = if ($script:FAIL -gt 0) { "Red" } else { "Green" }
Write-Host " Failed : $($script:FAIL)" -ForegroundColor $fc
if ($script:ERRORS.Count -gt 0) {
    Write-Host ""
    Write-Host " Failed checks:" -ForegroundColor Red
    foreach ($e in $script:ERRORS) { Write-Host "   - $e" -ForegroundColor Red }
}
Write-Host "============================================" -ForegroundColor Cyan

if ($script:FAIL -gt 0) { exit 1 } else { exit 0 }
