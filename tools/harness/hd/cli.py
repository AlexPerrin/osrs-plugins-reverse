"""hd: the harness v3 command line (python3 tools/harness/hd.py ...). Filled in with the modules."""
import argparse
import sys


def main(argv=None):
    p = argparse.ArgumentParser(prog="hd", description=__doc__)
    sp = p.add_subparsers(dest="cmd", required=True)
    sp.add_parser("version")
    a = p.parse_args(argv)
    if a.cmd == "version":
        print("hd 3.0 (foundation)")
        return 0
    return 2


if __name__ == "__main__":
    sys.exit(main())
