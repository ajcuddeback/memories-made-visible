#!/bin/bash
# Runs inside LocalStack once S3 is ready: create the photo bucket the dev profile uses.
awslocal s3 mb s3://mmv-photos 2>/dev/null || true
echo "mmv-photos bucket ready"
