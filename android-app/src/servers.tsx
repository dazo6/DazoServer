import React from "react";
import { View } from "react-native";
import { Button, IconButton, Input, Txt } from "./ui";
import { useUI } from "./theme";

export function ServerFields({
  host,
  setHost,
  backups,
  setBackups,
}: {
  host: string;
  setHost?(value: string): void;
  backups: string[];
  setBackups(value: string[]): void;
}) {
  const { S } = useUI();
  return (
    <>
      {setHost ? (
        <Input
          label="主服务器地址"
          placeholder="https://example.com"
          value={host}
          onChange={setHost}
        />
      ) : (
        <View style={{ marginBottom: 16 }}>
          <Txt size={12} muted>
            主服务器
          </Txt>
          <Txt size={14}>{host}</Txt>
        </View>
      )}
      {backups.map((url, index) => (
        <View key={index} style={[S.row, { gap: 4, alignItems: "flex-end" }]}>
          <View style={{ flex: 1 }}>
            <Input
              label={`备用地址 ${index + 1}`}
              placeholder="https://backup.example.com"
              value={url}
              onChange={(next) =>
                setBackups(backups.map((old, i) => (i === index ? next : old)))
              }
            />
          </View>
          <View style={{ marginBottom: 16 }}>
            <IconButton
              name="close"
              label={`删除备用地址 ${index + 1}`}
              onPress={() => setBackups(backups.filter((_, i) => i !== index))}
            />
          </View>
        </View>
      ))}
      {backups.length < 5 && (
        <Button
          title="添加备用地址"
          quiet
          onPress={() => setBackups([...backups, ""])}
        />
      )}
    </>
  );
}
