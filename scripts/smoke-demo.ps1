param([string]$BaseUrl = 'http://127.0.0.1:8080/api/v1')
$ErrorActionPreference = 'Stop'
# 10/20：本脚本会写入并删除一条演示点评，只用于独立的本地 demo 数据库。
function Request-Api($Method, $Path, $Token = '', $Data = $null) {
    $options = @{ Method = $Method; Uri = "$BaseUrl$Path"; Headers = @{} }
    if ($Token) { $options.Headers.Authorization = "Bearer $Token" }
    if ($null -ne $Data) {
        $options.ContentType = 'application/json; charset=utf-8'
        $options.Body = [Text.Encoding]::UTF8.GetBytes(($Data | ConvertTo-Json -Compress))
    }
    return Invoke-RestMethod @options
}
function Require-Success($Response) {
    if ($Response.code -ne 0) { throw "接口失败：$($Response.msg) [$($Response.code)]" }
    return $Response.data
}
$campuses = Require-Success (Request-Api GET '/campuses')
if ($campuses.Count -lt 1) { throw '缺少校区示例数据。' }
# 10/21：真实登录拦截器必须区分未登录与失效会话，不能直接进入业务接口。
if ((Request-Api GET '/auth/me').code -ne 10001) { throw '匿名请求未被拦截。' }
if ((Request-Api GET '/auth/me' 'invalid-smoke-session').code -ne 10002) { throw '失效会话未被拦截。' }
$user = Require-Success (Request-Api POST '/auth/login' '' @{ code = 'demo-user' })
$admin = Require-Success (Request-Api POST '/auth/login' '' @{ code = 'demo-admin' })
if ($user.user.role -ne 0 -or $admin.user.role -ne 1) { throw '演示身份权限不符。' }
Require-Success (Request-Api PUT '/auth/campus' $user.token @{ campusId = $campuses[0].id }) | Out-Null
Require-Success (Request-Api GET '/auth/me' $user.token) | Out-Null
$denied = Request-Api GET '/admin/reviews' $user.token
if ($denied.code -eq 0) { throw '普通用户不应拥有管理权限。' }
$before = Require-Success (Request-Api GET '/dishes/1' $user.token)
$review = $null
try {
    $review = Require-Success (Request-Api POST '/reviews' $user.token @{
        module = 'food'; targetType = 'dish'; targetId = 1; rating = 5
        content = '这是一条本地启动验收点评，分量充足味道很好。'
    })
    if ($review.auditStatus -ne 3) { throw '演示点评应进入人工复审。' }
    $queue = Require-Success (Request-Api GET '/admin/reviews?auditStatus=3' $admin.token)
    if ($review.id -notin @($queue.list | ForEach-Object { $_.id })) { throw '复审队列未包含新点评。' }
    Require-Success (Request-Api POST "/admin/audit/$($review.id)/confirm" $admin.token @{ action = 'pass' }) | Out-Null
    $after = Require-Success (Request-Api GET '/dishes/1' $user.token)
    if ($after.ratingCount -ne ($before.ratingCount + 1)) { throw '复审通过后评分计数未更新。' }
    Require-Success (Request-Api GET '/rankings/hot-dishes?days=7' $user.token) | Out-Null
    # 10/20：另一个身份举报已公开点评，验证工单处理和下架后的真实评分变化。
    $reportId = Require-Success (Request-Api POST '/reports' $admin.token @{
        reviewId = $review.id; reasonType = 6; reasonDetail = '本地启动验收演示举报'
    })
    Require-Success (Request-Api POST "/admin/reports/$reportId/handle" $admin.token @{
        action = 'block'; handleResult = '本地启动验收下架演示点评'
    }) | Out-Null
    $blocked = Require-Success (Request-Api GET '/dishes/1' $user.token)
    if ($blocked.ratingCount -ne $before.ratingCount) { throw '举报下架后评分计数未恢复。' }
} finally {
    if ($null -ne $review) {
        Require-Success (Request-Api DELETE "/reviews/$($review.id)" $user.token) | Out-Null
    }
}
$restored = Require-Success (Request-Api GET '/dishes/1' $user.token)
if ($restored.ratingCount -ne $before.ratingCount) { throw '删除后评分计数未恢复。' }
# 10/21：变换伪造代理头仍应共用同一登录计数键；仅在业务验收结束后触发限流。
for ($attempt = 1; $attempt -le 6; $attempt++) {
    $rateResult = Invoke-RestMethod -Method POST -Uri "$BaseUrl/auth/login" `
        -ContentType 'application/json' -Body '{"code":"invalid-smoke-code"}' `
        -Headers @{ 'X-Forwarded-For' = "192.0.2.$attempt"; 'X-Real-IP' = "198.51.100.$attempt" }
    if ($rateResult.code -notin @(20001, 30001)) { throw '限流验收返回非预期结果。' }
}
if ($rateResult.code -ne 30001) { throw '伪造代理头绕过了登录限流。' }
Write-Output '演示验收通过：校区、真实会话、登录拦截、个人资料、权限隔离、点评、复审、评分、榜单、举报处理、删除、登录限流。'
