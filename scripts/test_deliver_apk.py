import importlib.util
import os
import unittest

_spec = importlib.util.spec_from_file_location(
    "deliver_apk", os.path.join(os.path.dirname(__file__), "deliver-apk.py")
)
deliver = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(deliver)

SAMPLE = (
    "List of devices attached\r\n"
    "R58M123ABC             device usb:1-1 product:x model:SM_G991B device:y transport_id:1\r\n"
    "emulator-5554          device product:sdk model:sdk_gphone transport_id:2\r\n"
    "ZZZ999                 unauthorized usb:1-2 transport_id:3\r\n"
    "OFF111                 offline\r\n"
    "\r\n"
)


class DeliverApkTest(unittest.TestCase):
    def test_parse_devices(self):
        devices = deliver.parse_devices(SAMPLE)
        self.assertEqual(
            devices,
            [("R58M123ABC", "device"), ("emulator-5554", "device"),
             ("ZZZ999", "unauthorized"), ("OFF111", "offline")],
        )

    def test_excludes_emulators_by_default(self):
        serials, warnings = deliver.select_serials(deliver.parse_devices(SAMPLE), False)
        self.assertEqual(serials, ["R58M123ABC"])
        self.assertEqual(len(warnings), 2)

    def test_includes_emulators_on_request(self):
        serials, _ = deliver.select_serials(deliver.parse_devices(SAMPLE), True)
        self.assertEqual(serials, ["R58M123ABC", "emulator-5554"])


if __name__ == "__main__":
    unittest.main()
