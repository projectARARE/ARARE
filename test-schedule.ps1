##############################################################
# ARARE - Full End-to-End Schedule Generation Test Script
# Uses Invoke-RestMethod (PowerShell native)
# Run: powershell.exe -ExecutionPolicy Bypass -File .\test-schedule.ps1
##############################################################

$BASE = "http://localhost:8080/api/v1"
$ErrorActionPreference = "Stop"

function irm_get($url) {
    try {
        $r = Invoke-RestMethod -Uri $url -Method GET -ContentType "application/json"
        return $r
    } catch {
        Write-Host "  GET $url failed: $($_.Exception.Message)" -ForegroundColor Red
        throw
    }
}

function irm_post($url, $body) {
    try {
        $json = $body | ConvertTo-Json -Depth 10
        $r = Invoke-RestMethod -Uri $url -Method POST -Body $json -ContentType "application/json"
        return $r
    } catch {
        $msg = $_.Exception.Response | Out-String
        Write-Host "  POST $url failed: $($_.Exception.Message)" -ForegroundColor Red
        try {
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            $reader.BaseStream.Position = 0
            $reader.DiscardBufferedData()
            $body_err = $reader.ReadToEnd()
            Write-Host "  Response body: $body_err" -ForegroundColor Red
        } catch {}
        throw
    }
}

function irm_put($url, $body) {
    try {
        $json = $body | ConvertTo-Json -Depth 10
        $r = Invoke-RestMethod -Uri $url -Method PUT -Body $json -ContentType "application/json"
        return $r
    } catch {
        Write-Host "  PUT $url failed: $($_.Exception.Message)" -ForegroundColor Red
        throw
    }
}

function irm_delete($url) {
    try {
        Invoke-RestMethod -Uri $url -Method DELETE | Out-Null
    } catch {
        Write-Host "  DELETE $url failed: $($_.Exception.Message)" -ForegroundColor Red
    }
}

function Section($title) {
    Write-Host ""
    Write-Host "=== $title ===" -ForegroundColor Yellow
}
function Ok($msg)   { Write-Host "  [OK]  $msg" -ForegroundColor Green }
function Err($msg)  { Write-Host "  [ERR] $msg" -ForegroundColor Red }
function Info($msg) { Write-Host "  $msg"        -ForegroundColor Gray }

##############################################################
# STEP 0 - Health check
##############################################################
Section "STEP 0: Health check"
try {
    irm_get "$BASE/buildings" | Out-Null
    Ok "Backend is reachable at $BASE"
} catch {
    Err "Backend not reachable at $BASE. Start the Spring Boot app first."
    exit 1
}

##############################################################
# STEP 1 - Building
##############################################################
Section "STEP 1: Building"
$buildings = @(irm_get "$BASE/buildings")
if ($buildings.Count -gt 0) {
    $building = $buildings[0]
    Ok "Using existing building: $($building.name) (id=$($building.id))"
} else {
    $building = irm_post "$BASE/buildings" @{ name = "Main Block"; location = "Central Campus" }
    Ok "Created building id=$($building.id)"
}

##############################################################
# STEP 2 - Department
##############################################################
Section "STEP 2: Department"
$departments = @(irm_get "$BASE/departments")
if ($departments.Count -gt 0) {
    $dept = $departments[0]
    Ok "Using existing department: $($dept.name) (id=$($dept.id))"
} else {
    $dept = irm_post "$BASE/departments" @{
        name       = "Computer Science"
        code       = "CSE"
        buildingIds = @($building.id)
    }
    Ok "Created department id=$($dept.id)"
}

##############################################################
# STEP 3 - Rooms (1 lecture + 1 lab)
##############################################################
Section "STEP 3: Rooms"
$rooms       = @(irm_get "$BASE/rooms")
$lectureRoom = $rooms | Where-Object { $_.type -eq "LECTURE" } | Select-Object -First 1
$labRoom     = $rooms | Where-Object { $_.type -eq "LAB"     } | Select-Object -First 1

if (-not $lectureRoom) {
    $lectureRoom = irm_post "$BASE/rooms" @{
        buildingId = $building.id
        roomNumber = "LH-101"
        type       = "LECTURE"
        capacity   = 60
        availableTimeslotIds = @()
    }
    Ok "Created lecture room id=$($lectureRoom.id)"
} else {
    Ok "Using lecture room: $($lectureRoom.roomNumber) (id=$($lectureRoom.id))"
}

if (-not $labRoom) {
    $labRoom = irm_post "$BASE/rooms" @{
        buildingId   = $building.id
        roomNumber   = "LAB-201"
        type         = "LAB"
        labSubtype   = "COMPUTER_LAB"
        capacity     = 30
        availableTimeslotIds = @()
    }
    Ok "Created lab room id=$($labRoom.id)"
} else {
    Ok "Using lab room: $($labRoom.roomNumber) (id=$($labRoom.id))"
}

##############################################################
# STEP 4 - Timeslots: regular shift Mon-Fri
# NOTE: backend requires HH:MM:SS format for LocalTime
##############################################################
Section "STEP 4: Timeslots (regular shift Mon-Fri)"
$tsdays = @("MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY")
$periods = @(
    [PSCustomObject]@{ start = "09:30:00"; end = "10:20:00"; type = "CLASS" },
    [PSCustomObject]@{ start = "10:20:00"; end = "11:10:00"; type = "CLASS" },
    [PSCustomObject]@{ start = "11:10:00"; end = "12:00:00"; type = "CLASS" },
    [PSCustomObject]@{ start = "12:00:00"; end = "12:40:00"; type = "BREAK" },
    [PSCustomObject]@{ start = "12:40:00"; end = "13:30:00"; type = "CLASS" },
    [PSCustomObject]@{ start = "13:30:00"; end = "14:20:00"; type = "CLASS" },
    [PSCustomObject]@{ start = "14:20:00"; end = "15:10:00"; type = "CLASS" }
)

$existingTS = @(irm_get "$BASE/timeslots")
$createdTS  = 0
$skippedTS  = 0

foreach ($day in $tsdays) {
    foreach ($p in $periods) {
        $already = $existingTS | Where-Object {
            $_.day -eq $day -and $_.startTime -like "$($p.start.Substring(0,5))*"
        }
        if (-not $already) {
            irm_post "$BASE/timeslots" @{
                day       = $day
                startTime = $p.start
                endTime   = $p.end
                type      = $p.type
            } | Out-Null
            $createdTS++
        } else {
            $skippedTS++
        }
    }
}
Ok "Timeslots: $createdTS created, $skippedTS already existed"

$allTimeslots = @(irm_get "$BASE/timeslots")
$classSlots   = @($allTimeslots | Where-Object { $_.type -eq "CLASS" })
Info "Total CLASS timeslots: $($classSlots.Count)"

##############################################################
# STEP 5 - Subjects (3 lecture + 1 lab)
##############################################################
Section "STEP 5: Subjects"
$existingSubs = @(irm_get "$BASE/subjects")

$subjectDefs = @(
    @{ name="Data Structures";  code="CS201";  weeklyHours=4; chunkHours=1; isLab=$false; roomTypeRequired="LECTURE"; maxSessionsPerDay=2 },
    @{ name="Mathematics I";    code="MA101";  weeklyHours=3; chunkHours=1; isLab=$false; roomTypeRequired="LECTURE"; maxSessionsPerDay=1 },
    @{ name="Programming in C"; code="CS101";  weeklyHours=3; chunkHours=1; isLab=$false; roomTypeRequired="LECTURE"; maxSessionsPerDay=1 },
    @{ name="Computer Lab";     code="CS101L"; weeklyHours=2; chunkHours=2; isLab=$true;  roomTypeRequired="LAB";     maxSessionsPerDay=1; labSubtypeRequired="COMPUTER_LAB" }
)

$subjectIds = @()
foreach ($def in $subjectDefs) {
    $already = $existingSubs | Where-Object { $_.code -eq $def.code }
    if ($already) {
        Ok "Using existing subject: $($already.name) (id=$($already.id))"
        $subjectIds += [int]$already.id
    } else {
        $payload = @{
            name                  = $def.name
            code                  = $def.code
            departmentId          = [int]$dept.id
            weeklyHours           = [int]$def.weeklyHours
            chunkHours            = [int]$def.chunkHours
            isLab                 = [bool]$def.isLab
            roomTypeRequired      = $def.roomTypeRequired
            requiresTeacher       = $true
            requiresRoom          = $true
            maxSessionsPerDay     = [int]$def.maxSessionsPerDay
            minGapBetweenSessions = 0
        }
        if ($def.labSubtypeRequired) { $payload.labSubtypeRequired = $def.labSubtypeRequired }
        $s = irm_post "$BASE/subjects" $payload
        $subjectIds += [int]$s.id
        Ok "Created subject: $($def.name) (id=$($s.id))"
    }
}
Info "Subject IDs: $($subjectIds -join ', ')"

##############################################################
# STEP 6 - Teacher (assign ALL dept subjects to all teachers)
##############################################################
Section "STEP 6: Teacher"

# Collect ALL subject IDs for this department (not just the ones we just created)
$allDeptSubjects  = @(irm_get "$BASE/subjects" | Where-Object { $_.departmentId -eq $dept.id })
$allDeptSubjectIds = @($allDeptSubjects | ForEach-Object { [int]$_.id })
Info "All department subject IDs: $($allDeptSubjectIds -join ', ')"

$teachers = @(irm_get "$BASE/teachers")
if ($teachers.Count -gt 0) {
    $teacher = $teachers[0]
    Ok "Using existing teacher: $($teacher.name) (id=$($teacher.id))"
} else {
    $teacher = irm_post "$BASE/teachers" @{
        name                  = "Prof. John Doe"
        subjectIds            = $allDeptSubjectIds
        availableTimeslotIds  = @()
        preferredBuildingIds  = @()
        maxDailyHours         = 6
        maxWeeklyHours        = 20
        maxConsecutiveClasses = 3
        movementPenalty       = 1
    }
    Ok "Created teacher id=$($teacher.id)"
}

# Update ALL teachers to cover all dept subjects (testing environment convenience)
foreach ($t in $teachers) {
    irm_put "$BASE/teachers/$($t.id)" @{
        name                  = $t.name
        subjectIds            = $allDeptSubjectIds
        availableTimeslotIds  = @()
        preferredBuildingIds  = @()
        maxDailyHours         = 8
        maxWeeklyHours        = 30
        maxConsecutiveClasses = 5
        movementPenalty       = 1
    } | Out-Null
}
Ok "Updated $($teachers.Count) teacher(s) - each qualified for $($allDeptSubjectIds.Count) subject(s)"

# Always update teacher to ensure they have all subjects (kept for compat)
$updated = irm_put "$BASE/teachers/$($teacher.id)" @{
    name                  = $teacher.name
    subjectIds            = $allDeptSubjectIds
    availableTimeslotIds  = @()
    preferredBuildingIds  = @()
    maxDailyHours         = 6
    maxWeeklyHours        = 20
    maxConsecutiveClasses = 5
    movementPenalty       = 1
}
Ok "Teacher '$($updated.name)' assigned $($allDeptSubjectIds.Count) subject(s)"

##############################################################
# STEP 7 - Batch
##############################################################
Section "STEP 7: Batch"
$batches = @(irm_get "$BASE/batches")
$batch = $batches | Where-Object { $_.year -eq 1 -and $_.section -eq "A" } | Select-Object -First 1
if (-not $batch) {
    $batch = irm_post "$BASE/batches" @{
        departmentId = [int]$dept.id
        year         = 1
        section      = "A"
        studentCount = 60
        workingDays  = @("MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY")
    }
    Ok "Created batch id=$($batch.id)"
} else {
    Ok "Using existing batch: $($batch.departmentName) Year $($batch.year)-$($batch.section) (id=$($batch.id))"
}

##############################################################
# STEP 8 - Class Sections for lab splits
##############################################################
Section "STEP 8: Class Sections (lab sub-groups)"
$sections    = @(irm_get "$BASE/class-sections/batch/$($batch.id)")
$labSection1 = $sections | Where-Object { $_.label -eq "A" } | Select-Object -First 1
$labSection2 = $sections | Where-Object { $_.label -eq "B" } | Select-Object -First 1

if (-not $labSection1) {
    $labSection1 = irm_post "$BASE/class-sections" @{ batchId = [int]$batch.id; label = "A"; size = 30 }
    Ok "Created section A id=$($labSection1.id)"
} else {
    Ok "Section A id=$($labSection1.id)"
}

if (-not $labSection2) {
    $labSection2 = irm_post "$BASE/class-sections" @{ batchId = [int]$batch.id; label = "B"; size = 30 }
    Ok "Created section B id=$($labSection2.id)"
} else {
    Ok "Section B id=$($labSection2.id)"
}

##############################################################
# STEP 9 - University Config
##############################################################
Section "STEP 9: University Config"
try {
    $config = irm_get "$BASE/university-config"
    Ok "Config exists (id=$($config.id), days=$($config.daysPerWeek))"
} catch {
    $config = irm_post "$BASE/university-config" @{
        daysPerWeek      = 5
        timeslotsPerDay  = 7
        maxClassesPerDay = 6
        breakSlotIndices = @(3)
        workingDays      = @("MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY")
    }
    Ok "Config created (id=$($config.id))"
}

##############################################################
# STEP 10 - Pre-flight check
##############################################################
Section "STEP 10: Pre-flight summary"
$finalSlots   = @(irm_get "$BASE/timeslots" | Where-Object { $_.type -eq "CLASS" }).Count
$finalRooms   = @(irm_get "$BASE/rooms").Count
$finalTeach   = @(irm_get "$BASE/teachers").Count
$finalSubs    = @(irm_get "$BASE/subjects").Count
$finalBatches = @(irm_get "$BASE/batches").Count

Info "CLASS timeslots : $finalSlots"
Info "Rooms           : $finalRooms"
Info "Teachers        : $finalTeach"
Info "Subjects        : $finalSubs"
Info "Batches         : $finalBatches"

$issues = 0
if ($finalSlots   -lt 10) { Err "Not enough CLASS timeslots (need >= 10)"; $issues++ }
if ($finalRooms   -lt 1)  { Err "No rooms configured";                     $issues++ }
if ($finalTeach   -lt 1)  { Err "No teachers configured";                  $issues++ }
if ($finalSubs    -lt 1)  { Err "No subjects configured";                  $issues++ }
if ($finalBatches -lt 1)  { Err "No batches configured";                   $issues++ }

if ($issues -gt 0) {
    Err "Pre-flight failed with $issues issue(s). Aborting."
    exit 1
}
Ok "Pre-flight passed"

##############################################################
# STEP 11 - Generate Schedule
##############################################################
Section "STEP 11: Generate Schedule"
Write-Host "  Sending generation request (solver runs ~30s)..." -ForegroundColor Gray

$sw = [System.Diagnostics.Stopwatch]::StartNew()
try {
    $schedule = irm_post "$BASE/schedules/generate" @{
        name         = "CSE Year-1 Test Schedule"
        scope        = "DEPARTMENT"
        departmentId = [int]$dept.id
    }
    $sw.Stop()
    Ok "Schedule generated in $($sw.Elapsed.TotalSeconds.ToString('F1'))s"
    Info "ID     : $($schedule.id)"
    Info "Name   : $($schedule.name)"
    Info "Status : $($schedule.status)"
    Info "Score  : $($schedule.score)"
} catch {
    $sw.Stop()
    Err "Generation failed after $($sw.Elapsed.TotalSeconds.ToString('F1'))s: $($_.Exception.Message)"
    try {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $reader.BaseStream.Position = 0
        $reader.DiscardBufferedData()
        $body_err = $reader.ReadToEnd()
        Write-Host "  Server error: $body_err" -ForegroundColor Red
    } catch {}
    exit 1
}

##############################################################
# STEP 12 - Verify sessions
##############################################################
Section "STEP 12: Verify assigned sessions"
$sessions   = @(irm_get "$BASE/schedules/$($schedule.id)/sessions")
$total      = $sessions.Count
$assigned   = @($sessions | Where-Object { $null -ne $_.timeslotId }).Count
$unassigned = $total - $assigned

# Conflict checks
$byBatchSlot   = $sessions | Where-Object { $null -ne $_.batchId -and $null -ne $_.timeslotId } |
    Group-Object { "$($_.batchId)-$($_.timeslotId)" }
$batchConflicts = @($byBatchSlot | Where-Object { $_.Count -gt 1 }).Count

$byTeacherSlot  = $sessions | Where-Object { $null -ne $_.teacherId -and $null -ne $_.timeslotId } |
    Group-Object { "$($_.teacherId)-$($_.timeslotId)" }
$teacherConflicts = @($byTeacherSlot | Where-Object { $_.Count -gt 1 }).Count

$byRoomSlot     = $sessions | Where-Object { $null -ne $_.roomId -and $null -ne $_.timeslotId } |
    Group-Object { "$($_.roomId)-$($_.timeslotId)" }
$roomConflicts  = @($byRoomSlot | Where-Object { $_.Count -gt 1 }).Count

Info "Total sessions : $total"
Info "Assigned       : $assigned"
Info "Unassigned     : $unassigned"

if ($batchConflicts   -eq 0) { Ok "No batch double-bookings"   } else { Err "$batchConflicts batch conflict(s)!"   }
if ($teacherConflicts -eq 0) { Ok "No teacher double-bookings" } else { Err "$teacherConflicts teacher conflict(s)!" }
if ($roomConflicts    -eq 0) { Ok "No room double-bookings"    } else { Err "$roomConflicts room conflict(s)!"    }

##############################################################
# STEP 13 - Print timetable
##############################################################
Section "STEP 13: Timetable view"
$ttdays = @("MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY")
foreach ($day in $ttdays) {
    $daySessions = @($sessions | Where-Object { $_.day -eq $day } | Sort-Object startTime)
    if ($daySessions.Count -eq 0) { continue }
    Write-Host ""
    Write-Host "  $day" -ForegroundColor White
    foreach ($s in $daySessions) {
        $subj    = if ($s.subjectName) { $s.subjectName }  else { "?" }
        $teacher = if ($s.teacherName) { $s.teacherName }  else { "UNASSIGNED" }
        $room    = if ($s.roomNumber)  { $s.roomNumber }   else { "UNASSIGNED" }
        $start   = if ($s.startTime)   { $s.startTime.Substring(0,5) } else { "?" }
        $lbl     = if ($s.isLab)       { "[LAB]" }          else { "     " }
        Write-Host ("    {0,-6} {1,-24} {2,-6} {3,-22} {4}" -f $start, $subj, $lbl, $teacher, $room) -ForegroundColor Cyan
    }
}

##############################################################
# STEP 14 - Score explanation
##############################################################
Section "STEP 14: Score explanation"
try {
    $expl = irm_get "$BASE/schedules/$($schedule.id)/score-explanation"
    Info "Score    : $($expl.score)"
    Info "Feasible : $($expl.feasible)"
    if ($null -ne $expl.constraints -and $expl.constraints.Count -gt 0) {
        Write-Host ""
        Write-Host "  Constraint violations:" -ForegroundColor Yellow
        foreach ($c in $expl.constraints) {
            $color = if ($c.level -eq "HARD") { "Red" } elseif ($c.level -eq "MEDIUM") { "Yellow" } else { "Gray" }
            Write-Host ("    [{0,-6}] {1,-40} matches={2} impact={3}" -f $c.level, $c.constraintName, $c.matchCount, $c.scoreImpact) -ForegroundColor $color
        }
    } else {
        Ok "No constraint violations - fully feasible!"
    }
} catch {
    Info "Score explanation not available: $($_.Exception.Message)"
}

##############################################################
# RESULT
##############################################################
Section "RESULT"
if ($schedule.status -eq "ACTIVE") {
    Ok "Schedule is ACTIVE (hard-feasible). Generation SUCCEEDED!"
} elseif ($schedule.status -eq "PARTIAL") {
    Write-Host "  [WARN] Schedule is PARTIAL - some hard constraints could not be satisfied." -ForegroundColor Yellow
    Write-Host "         Check violations above and consider adding more rooms/timeslots/teachers." -ForegroundColor Yellow
} else {
    Err "Schedule status: $($schedule.status)"
}
Write-Host ""
