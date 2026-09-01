python data_audit.py
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

python train_models.py
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

python evaluate_models.py
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

python ablation_experiments.py
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

python calibration.py
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
