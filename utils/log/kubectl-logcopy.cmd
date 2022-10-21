set pod=%1
echo %pod%
for /F "tokens=*" %%a in (kubectl-logcopy-files.txt) do (
	kubectl cp %1:ticket/appdata/logs/%%a %%a
	)
