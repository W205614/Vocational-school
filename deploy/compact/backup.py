import argparse
from maintenance import perform_backup
parser=argparse.ArgumentParser();parser.add_argument('--online',action='store_true');args=parser.parse_args()
perform_backup(online=args.online)
